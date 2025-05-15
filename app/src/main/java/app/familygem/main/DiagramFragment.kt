package app.familygem.main

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.view.ContextMenu
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.TextUtilsCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import app.familygem.DiagramSettingsActivity
import app.familygem.GedcomDateConverter
import app.familygem.Global
import app.familygem.Memory
import app.familygem.NewRelativeDialog
import app.familygem.PersonEditorActivity
import app.familygem.R
import app.familygem.U
import app.familygem.constant.Choice
import app.familygem.constant.Extra
import app.familygem.constant.FileType
import app.familygem.constant.Relation
import app.familygem.databinding.DiagramFragmentBinding
import app.familygem.detail.FamilyActivity
import app.familygem.profile.ProfileActivity
import app.familygem.util.ChangeUtil
import app.familygem.util.FamilyUtil
import app.familygem.util.FileUtil
import app.familygem.util.PersonUtil
import app.familygem.util.TreeUtil
import app.familygem.util.Util.confirmDelete
import app.familygem.util.delete
import app.familygem.util.getFamilyLabels
import app.familygem.util.getSpouseRefs
import app.familygem.util.sex
import graph.gedcom.Bond
import graph.gedcom.CurveLine
import graph.gedcom.Graph
import graph.gedcom.Line
import graph.gedcom.Metric
import graph.gedcom.PersonNode
import graph.gedcom.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.folg.gedcom.model.Family
import org.folg.gedcom.model.Media
import org.folg.gedcom.model.Person
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class DiagramFragment : BaseFragment(R.layout.diagram_fragment) {

    private lateinit var binding: DiagramFragmentBinding
    private val graph = Graph() // Creates a diagram model
    private lateinit var moveLayout: MoveLayout
    private lateinit var box: RelativeLayout
    private var fulcrumView: GraphicPerson? = null
    private var glow: FulcrumGlow? = null
    private lateinit var lines: Lines
    private lateinit var backLines: Lines
    private lateinit var duplicateLines: DuplicateLines
    private var density = 0f
    private var lineStroke = 0 // Lines thickness, in pixels
    private var lineDash = 0 // Dashed lines interval
    private var glowSpace = 0 // Space to display glow around cards
    private var popup: View? = null // Suggestion balloon
    private var printing = false // We are generating a PNG or PDF
    private val leftToRight = TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_LTR
    private var diagramJob: Job? = null
    private val graphicNodes: MutableList<GraphicMetric> = ArrayList()
    private val loadingImages: MutableList<Pair<Media, ImageView>> = ArrayList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        density = resources.displayMetrics.density
        lineStroke = toPx(2f)
        lineDash = toPx(4f)
        glowSpace = toPx(35f)

        binding = DiagramFragmentBinding.inflate(inflater, container, false)
        binding.diagramHamburger.setOnClickListener {
            val drawer = requireActivity().findViewById<DrawerLayout>(R.id.main_layout)
            drawer.openDrawer(GravityCompat.START)
        }
        binding.diagramOptions.setOnClickListener {
            val settings = PopupMenu(context(), it)
            val menu = settings.menu
            menu.add(0, 0, 0, R.string.diagram_settings)
            if (Global.gc.people.size > 0) {
                menu.add(0, 1, 0, R.string.export_png)
                menu.add(0, 2, 0, R.string.export_pdf)
            }
            settings.show()
            settings.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    0 -> startActivity(Intent(context(), DiagramSettingsActivity::class.java)) // Diagram settings
                    1 -> generatePng() // Export PNG
                    2 -> generatePdf() // Export PDF
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
        }
        moveLayout = binding.diagramFrame
        moveLayout.graph = graph
        box = binding.diagramBox
        return binding.root
    }

    private fun context(): Context {
        return context ?: Global.context
    }

    // Called after choosePersonLauncher
    override fun onResume() {
        // Reasons why we should continue, especially things that have changed
        mayShow = mayShow || graph.fulcrum == null || graph.fulcrum.id != Global.indi || graph.whichFamily != Global.familyNum
        super.onResume() // Must stay here to apply mayShow value
        // Sometimes MainActivity calls onPause() during the creation of the diagram, e.g. on entering multi window
        // therefore we need to check this state and in case restart diagram
        box.postDelayed({
            if (binding.diagramWheel.root.isVisible && diagramJob != null && diagramJob!!.isCancelled) {
                startDiagram()
            }
        }, 1000)
    }

    override fun showContent() {
        startDiagram()
    }

    /** Identifies the fulcrum person to start from, shows the button 'Add the first person' or starts the diagram. */
    private fun startDiagram() {
        // Finds fulcrum
        val ids = arrayOf(Global.indi, Global.settings.currentTree.root, U.findRootId(Global.gc))
        var fulcrum: Person? = null
        for (id in ids) {
            fulcrum = Global.gc.getPerson(id)
            if (fulcrum != null) {
                // Checks if fulcrum is one of the first two spouses
                val spouseRefs = fulcrum.getSpouseFamilies(Global.gc).firstOrNull()?.getSpouseRefs()
                if (spouseRefs != null && spouseRefs.indexOfFirst { it.ref == id } >= 2) {
                    fulcrum = Global.gc.getPerson(spouseRefs[0].ref)
                    if (fulcrum != null) break
                } else break
            }
        }
        // Empty diagram
        if (fulcrum == null) {
            box.removeAllViews()
            val button = LayoutInflater.from(context()).inflate(R.layout.diagram_button, null)
            button.findViewById<View>(R.id.diagram_new).setOnClickListener {
                startActivity(Intent(context(), PersonEditorActivity::class.java))
            }
            SuggestionBalloon(context(), button, R.string.new_person)
            if (!Global.settings.expert) binding.diagramOptions.visibility = View.GONE
        } else {
            binding.diagramWheel.root.visibility = View.VISIBLE
            diagramJob = lifecycleScope.launch(Dispatchers.Default) {
                Global.indi = fulcrum.id // Confirms it just in case
                val settings = Global.settings.diagram
                graph.setGedcom(Global.gc).setLayoutDirection(leftToRight)
                    .maxAncestors(settings.ancestors)
                    .maxGreatUncles(settings.uncles)
                    .displaySpouses(settings.spouses)
                    .maxDescendants(settings.descendants)
                    .maxSiblingsNephews(settings.siblings)
                    .maxUnclesCousins(settings.cousins)
                    .displayNumbers(settings.numbers)
                    .displayDuplicateLines(settings.duplicates)
                    .showFamily(Global.familyNum)
                    .startFrom(fulcrum)
                drawDiagram()
            }
        }
    }

    /**
     * Creates cards, waits for all images loaded, detects card sizes and adds other elements to layout.
     * Called each time the fragment starts and clicking on a card.
     */
    private suspend fun drawDiagram() = coroutineScope {
        graphicNodes.clear()
        loadingImages.clear()
        // Place various type of graphic nodes in 'graphicNodes' list
        for (personNode in graph.personNodes) {
            ensureActive()
            if (personNode.mini) graphicNodes.add(GraphicMiniCard(context(), personNode))
            else graphicNodes.add(GraphicPerson(context(), personNode))
        }
        // First layout of cards
        graphicNodes.forEach {
            ensureActive()
            it.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.AT_MOST)
            it.layout(0, 0, it.measuredWidth, it.measuredHeight)
        }
        withContext(Dispatchers.Main) {
            // Loads images
            loadingImages.forEach {
                FileUtil.showImage(it.first, it.second)
            }
            // To stop waiting for images to be loaded
            binding.diagramWheel.root.setOnClickListener {
                loadingImages.forEach { it.second.tag = R.id.tag_object }
            }
        }
        // Waits for images to be loaded
        do {
            ensureActive()
            delay(100) // Anyway a little delay is useful to correctly calculate lines and pan to fulcrum
            var imageToLoad = false
            for (it in loadingImages) {
                if (it.second.tag != R.id.tag_object) {
                    imageToLoad = true
                    break
                }
            }
        } while (imageToLoad)
        // Only one person in the diagram
        if (graphicNodes.size == 1) {
            withContext(Dispatchers.Main) {
                // Puts the card under the suggestion balloon
                val singleNode = graphicNodes[0]
                singleNode.id = R.id.tag_fulcrum
                box.removeAllViews()
                val popupLayout: ConstraintLayout = SuggestionBalloon(context(), singleNode, R.string.long_press_menu)
                // Adds the glow to the fulcrum card
                if (fulcrumView != null) {
                    box.post {
                        val glowParams = ConstraintLayout.LayoutParams(singleNode.width + glowSpace * 2, singleNode.height + glowSpace * 2)
                        glowParams.topToTop = R.id.tag_fulcrum
                        glowParams.bottomToBottom = R.id.tag_fulcrum
                        glowParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                        glowParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                        fulcrumView!!.metric.width = toDp(singleNode.width)
                        fulcrumView!!.metric.height = toDp(singleNode.height)
                        popupLayout.addView(FulcrumGlow(context), 0, glowParams)
                    }
                }
                binding.diagramWheel.root.visibility = View.GONE
            }
        } else { // Two or more persons in the diagram or PDF print
            // Gets the dimensions of each node converting from pixel to dip
            graphicNodes.forEach {
                ensureActive()
                // Second measurement to get final card size
                it.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
                it.metric.width = toDp(it.measuredWidth)
                it.metric.height = toDp(it.measuredHeight)
            }
            graph.initNodes() // Initializes nodes and lines
            // Adds bond nodes
            for (bond in graph.bonds) {
                graphicNodes.add(GraphicBond(context(), bond))
            }
            graph.placeNodes() // Calculates final position
            // Other diagram elements
            withContext(Dispatchers.Main) {
                box.removeAllViews()
                // Adds the lines
                lines = Lines(context(), graph.lines, false)
                box.addView(lines, 0)
                backLines = Lines(context(), graph.backLines, true)
                box.addView(backLines, 0)
                duplicateLines = DuplicateLines(context())
                box.addView(duplicateLines, 0)
                // Adds the glow
                val fulcrumNode = fulcrumView!!.metric as PersonNode
                val glowParams = RelativeLayout.LayoutParams(toPx(fulcrumNode.width) + glowSpace * 2, toPx(fulcrumNode.height) + glowSpace * 2)
                glowParams.rightMargin = -glowSpace
                glowParams.bottomMargin = -glowSpace
                box.addView(FulcrumGlow(context()), 0, glowParams)
                displaceDiagram()
            }
        }
    }

    /**
     * Place cards on their final position and updates glow and lines.
     */
    private fun displaceDiagram() {
        // Position of the nodes from dips to pixels
        graphicNodes.forEach {
            val params = it.layoutParams as RelativeLayout.LayoutParams
            params.leftMargin = toPx(it.metric.x)
            params.topMargin = toPx(it.metric.y)
            box.addView(it)
        }
        // The glow follows fulcrum
        val glowParams = glow!!.layoutParams as RelativeLayout.LayoutParams
        glowParams.leftMargin = toPx(fulcrumView!!.metric.x) - glowSpace
        glowParams.topMargin = toPx(fulcrumView!!.metric.y) - glowSpace
        moveLayout.childWidth = toPx(graph.width) + box.paddingStart * 2
        moveLayout.childHeight = toPx(graph.height) + box.paddingTop * 2
        // Updates lines
        lines.invalidate()
        backLines.invalidate()
        duplicateLines.invalidate()
        binding.diagramWheel.root.visibility = View.GONE
        // Pans to fulcrum
        val scale = moveLayout.minimumScale()
        val padding = box.paddingTop * scale
        moveLayout.panTo(
            (toPx(fulcrumView!!.metric.centerX()) * scale - moveLayout.width / 2 + padding).toInt(),
            (toPx(fulcrumView!!.metric.centerY()) * scale - moveLayout.height / 2 + padding).toInt()
        )
    }

    /**
     * Updates both diagram and main menu.
     */
    private fun refreshAll() {
        startDiagram()
        (requireActivity() as MainActivity).furnishMenu()
    }

    override fun onPause() {
        super.onPause()
        if (diagramJob != null && diagramJob!!.isActive) diagramJob!!.cancel()
    }

    /**
     * Puts a view under the suggestion balloon.
     */
    inner class SuggestionBalloon(context: Context, private val childView: View, suggestion: Int) : ConstraintLayout(context) {
        init {
            val popupView = layoutInflater.inflate(R.layout.popup, this, true)
            box.addView(popupView)
            val nodeParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            nodeParams.topToBottom = R.id.popup_fumetto
            nodeParams.startToStart = LayoutParams.PARENT_ID
            nodeParams.endToEnd = LayoutParams.PARENT_ID
            addView(childView, nodeParams)
            popup = popupView.findViewById(R.id.popup_fumetto)
            popup!!.findViewById<TextView>(R.id.popup_testo).setText(suggestion)
            popup!!.visibility = INVISIBLE
            popup!!.setOnTouchListener { view, event ->
                view.performClick() // Useless but required
                if (event.action == MotionEvent.ACTION_DOWN) {
                    view.visibility = INVISIBLE
                    return@setOnTouchListener true
                }
                false
            }
            postDelayed({
                moveLayout.childWidth = box.width
                moveLayout.childHeight = box.height
                moveLayout.displayAll()
            }, 100)
            popup!!.postDelayed({ popup!!.visibility = VISIBLE }, 1000)
        }

        override fun invalidate() {
            if (printing) {
                popup!!.visibility = GONE
                glow?.visibility = GONE
                childView.invalidate()
            }
        }
    }

    /**
     * The glow around fulcrum card.
     */
    inner class FulcrumGlow(context: Context?) : View(context) {
        private var paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var bmf = BlurMaskFilter(toPx(25f).toFloat(), BlurMaskFilter.Blur.NORMAL)
        private var extend = toPx(5f) // draw a rectangle a little bigger

        init {
            glow = this
        }

        override fun onDraw(canvas: Canvas) {
            paint.color = ResourcesCompat.getColor(resources, R.color.accent, null)
            paint.maskFilter = bmf
            setLayerType(LAYER_TYPE_SOFTWARE, paint)
            canvas.drawRect(
                (glowSpace - extend).toFloat(), (glowSpace - extend).toFloat(),
                (toPx(fulcrumView!!.metric.width) + glowSpace + extend).toFloat(),
                (toPx(fulcrumView!!.metric.height) + glowSpace + extend).toFloat(), paint
            )
        }

        override fun invalidate() {
            if (printing) {
                visibility = GONE
            }
        }
    }

    /**
     * Node with one person or one bond.
     */
    abstract inner class GraphicMetric(context: Context, val metric: Metric) : RelativeLayout(context) {
        init {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }
    }

    /**
     * Card of a person.
     */
    inner class GraphicPerson(context: Context, personNode: PersonNode) : GraphicMetric(context, personNode) {
        var background: ImageView

        init {
            val person = personNode.person
            id = U.extractNum(person.id)
            val view = layoutInflater.inflate(R.layout.diagram_card, this, true)
            val border = view.findViewById<View>(R.id.card_border)
            val sex = person.sex
            if (sex.isMale()) border.setBackgroundResource(R.drawable.person_border_male)
            else if (sex.isFemale()) border.setBackgroundResource(R.drawable.person_border_female)
            background = view.findViewById(R.id.card_background)
            if (person.id == Global.indi) {
                background.setBackgroundResource(R.drawable.person_background_selected)
            } else if (personNode.acquired) {
                background.setBackgroundResource(R.drawable.person_background_partner)
            }
            if (personNode.isFulcrumNode) fulcrumView = this
            val imageView = view.findViewById<ImageView>(R.id.card_picture)
            val media = FileUtil.selectMainImage(person, imageView, show = false)
            if (media != null) loadingImages.add(Pair(media, imageView))
            view.findViewById<TextView>(R.id.card_name).text = U.properName(person, true)
            val titleView = view.findViewById<TextView>(R.id.card_title)
            val title = PersonUtil.writeTitles(person)
            if (title.isEmpty()) titleView.visibility = GONE else titleView.text = title
            val dataView = view.findViewById<TextView>(R.id.card_data)
            val data = U.twoDates(person, true)
            if (data.isEmpty()) dataView.visibility = GONE else dataView.text = data
            if (!U.isDead(person)) view.findViewById<View>(R.id.card_mourn).visibility = GONE
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            registerForContextMenu(this)
            setOnClickListener {
                if (!diagramJob!!.isActive) { // Avoids multiple clicks
                    if (person.id == Global.indi) {
                        Memory.setLeader(person)
                        startActivity(Intent(getContext(), ProfileActivity::class.java))
                    } else {
                        clickCard(person)
                    }
                }
            }
        }

        override fun invalidate() {
            // Changes background color for PNG or PDF export
            if (printing) {
                if ((metric as PersonNode).acquired)
                    background.setBackgroundResource(R.drawable.person_background_partner_print)
                else // Removes fulcrum background
                    background.setBackgroundResource(R.drawable.person_background)
            }
        }
    }

    /**
     * Marriage with optional year and vertical line.
     */
    inner class GraphicBond(context: Context, bond: Bond) : GraphicMetric(context, bond) {
        private var hearth: View? = null

        init {
            val bondLayout = RelativeLayout(context)
            addView(bondLayout, LayoutParams(toPx(bond.width), toPx(bond.height)))
            val familyNode = bond.familyNode
            if (bond.marriageDate == null) {
                hearth = View(context)
                hearth!!.setBackgroundResource(R.drawable.diagram_hearth)
                val diameter = toPx((if (familyNode.mini) Util.MINI_HEARTH_DIAMETER else Util.HEARTH_DIAMETER).toFloat())
                val hearthParams = LayoutParams(diameter, diameter)
                hearthParams.topMargin = toPx(familyNode.centerRelY()) - diameter / 2
                hearthParams.addRule(CENTER_HORIZONTAL)
                bondLayout.addView(hearth, hearthParams)
            } else {
                val year = TextView(context)
                year.setBackgroundResource(R.drawable.diagram_year_oval)
                year.gravity = Gravity.CENTER
                year.text = GedcomDateConverter(bond.marriageDate).writeDate(true)
                year.textSize = 13f
                val yearParams = LayoutParams(LayoutParams.MATCH_PARENT, toPx(Util.MARRIAGE_HEIGHT.toFloat()))
                yearParams.topMargin = toPx(bond.centerRelY() - Util.MARRIAGE_HEIGHT / 2)
                bondLayout.addView(year, yearParams)
            }
            setOnClickListener {
                Memory.setLeader(familyNode.spouseFamily)
                startActivity(Intent(context, FamilyActivity::class.java))
            }
        }

        override fun invalidate() {
            if (printing && hearth != null) {
                hearth!!.setBackgroundResource(R.drawable.diagram_hearth_print)
            }
        }
    }

    /** Little ancestry or progeny card. */
    inner class GraphicMiniCard(context: Context, personNode: PersonNode) : GraphicMetric(context, personNode) {
        private val layout: RelativeLayout

        init {
            val miniCard = layoutInflater.inflate(R.layout.diagram_minicard, this, true)
            layout = miniCard.findViewById(R.id.minicard)
            val miniCardText = miniCard.findViewById<TextView>(R.id.minicard_text)
            miniCardText.text = if (personNode.amount > 100) "100+" else personNode.amount.toString()
            val sex = personNode.person.sex
            if (sex.isMale()) miniCardText.setBackgroundResource(R.drawable.person_border_male)
            else if (sex.isFemale()) miniCardText.setBackgroundResource(R.drawable.person_border_female)
            if (personNode.person.id == Global.indi) {
                layout.setBackgroundResource(R.drawable.person_background_selected)
            } else if (personNode.acquired) {
                layout.setBackgroundResource(R.drawable.person_background_partner)
            }
            miniCard.setOnClickListener { if (!diagramJob!!.isActive) clickCard(personNode.person) }
        }

        override fun invalidate() {
            if (printing) {
                if ((metric as PersonNode).acquired)
                    layout.setBackgroundResource(R.drawable.person_background_partner_print)
                else
                    layout.setBackgroundResource(R.drawable.person_background)
            }
        }
    }

    /** The view of lines connecting the cards. */
    inner class Lines(context: Context, private val lineGroups: List<Set<Line>>, private val dashed: Boolean) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val paths: MutableList<Path> = ArrayList() // Each path contains many lines
        private val biggestPath = graph.biggestPathSize
        private val maxBitmap = graph.maxBitmapSize
        private val matrix = Matrix()
        //private val colors = arrayOf(Color.WHITE, Color.RED, Color.CYAN, Color.MAGENTA, Color.GREEN, Color.BLACK, Color.YELLOW, Color.BLUE)

        init {
            paint.style = Paint.Style.STROKE
        }

        override fun invalidate() {
            paint.color = ResourcesCompat.getColor(resources, if (printing) R.color.diagram_lines_print else R.color.diagram_lines_screen, null)
            paths.clear() // In case of PNG or PDF print
            // Put the lines in one or more paths
            for ((pathNum, lineGroup) in lineGroups.withIndex()) {
                if (pathNum >= paths.size) paths.add(Path())
                val path = paths[pathNum]
                for (line in lineGroup) {
                    val x1 = toPxFloat(line.x1)
                    val y1 = toPxFloat(line.y1)
                    val x2 = toPxFloat(line.x2)
                    val y2 = toPxFloat(line.y2)
                    path.moveTo(x1, y1)
                    if (line is CurveLine) {
                        path.cubicTo(x1, y2, x2, y1, x2, y2)
                    } else { // Horizontal or vertical line
                        path.lineTo(x2, y2)
                    }
                }
            }
            // Possibly downscales paths and thickness
            var stroke = lineStroke.toFloat()
            val dashIntervals = floatArrayOf(lineDash.toFloat(), lineDash.toFloat())
            if (biggestPath > maxBitmap) {
                val factor = maxBitmap / biggestPath
                matrix.setScale(factor, factor)
                for (path in paths) {
                    path.transform(matrix)
                }
                stroke *= factor
                dashIntervals[0] *= factor
                dashIntervals[1] *= factor
            }
            paint.strokeWidth = stroke
            if (dashed) paint.pathEffect = DashPathEffect(dashIntervals, 0f)

            // Update this view size
            val params = layoutParams as RelativeLayout.LayoutParams
            params.width = toPx(graph.width)
            params.height = toPx(graph.height)
            requestLayout()
        }

        override fun onDraw(canvas: Canvas) {
            // Possibly upscale canvas
            if (biggestPath > maxBitmap) {
                val factor = biggestPath / maxBitmap
                matrix.setScale(factor, factor)
                canvas.concat(matrix)
            }
            // Draw the paths
            //paths.forEachIndexed { i, it -> paint.color = colors[i % colors.size];
            paths.forEach {
                canvas.drawPath(it, paint)
            }
        }
    }

    /** The view of lines connecting duplicated persons. */
    inner class DuplicateLines(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val paths: MutableList<Path> = ArrayList(3) // Each path contains many lines
        private val colors = arrayOf(R.color.male, R.color.female, R.color.undefined)

        init {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = toPxFloat(2F)
            paint.strokeCap = Paint.Cap.SQUARE
            for (i in 0..2) paths.add(Path())
        }

        override fun invalidate() {
            for (line in graph.duplicateLines) {
                val pathNum = when (line.gender) {
                    Util.Gender.MALE -> 0
                    Util.Gender.FEMALE -> 1
                    else -> 2
                }
                val path = paths[pathNum]
                path.moveTo(toPxFloat(line.x1), toPxFloat(line.y1))
                path.quadTo(toPxFloat(line.x3), toPxFloat(line.y3), toPxFloat(line.x2), toPxFloat(line.y2))
            }
            // Update this view size
            val params = layoutParams as RelativeLayout.LayoutParams
            params.width = toPx(graph.width)
            params.height = toPx(graph.height)
            requestLayout()
        }

        override fun onDraw(canvas: Canvas) {
            // Draw the paths
            paths.forEachIndexed { i, it ->
                paint.color = ResourcesCompat.getColor(resources, colors[i], null)
                canvas.drawPath(it, paint)
            }
        }
    }

    private fun clickCard(person: Person) {
        if (TreeUtil.isGlobalGedcomOk { selectParentFamily(person) }) selectParentFamily(person)
    }

    /** Asks which family to display in the diagram if fulcrum has many parent families. */
    private fun selectParentFamily(fulcrum: Person?) {
        val families = fulcrum!!.getParentFamilies(Global.gc)
        if (families.size > 1) {
            AlertDialog.Builder(context()).setTitle(R.string.which_family)
                .setItems(FamilyUtil.listFamilies(families)) { _, which: Int -> completeSelect(fulcrum, which) }.show()
        } else {
            completeSelect(fulcrum, 0)
        }
    }

    // Completes above function
    private fun completeSelect(fulcrum: Person, whichFamily: Int) {
        Global.indi = fulcrum.id
        Global.familyNum = whichFamily
        binding.diagramWheel.root.visibility = View.VISIBLE
        diagramJob = lifecycleScope.launch(Dispatchers.Default) {
            graph.showFamily(Global.familyNum)
            graph.startFrom(fulcrum)
            drawDiagram()
        }
    }

    private fun toDp(pixels: Int): Float {
        return pixels / density
    }

    private fun toPx(dips: Float): Int {
        return (dips * density + 0.5f).toInt()
    }

    private fun toPxFloat(dips: Float): Float {
        return dips * density + 0.5F
    }

    private lateinit var person: Person
    private lateinit var personId: String
    private var parentFam: Family? = null // Displayed family in which the person is child
    private var spouseFam: Family? = null // Selected family in which the person is spouse

    override fun onCreateContextMenu(menu: ContextMenu, view: View, info: ContextMenu.ContextMenuInfo?) {
        lateinit var personNode: PersonNode
        if (view is GraphicPerson) personNode = view.metric as PersonNode
        person = personNode.person
        if (personNode.origin != null) parentFam = personNode.origin.spouseFamily
        spouseFam = personNode.spouseFamily
        personId = person.id
        val familyLabels = person.getFamilyLabels(context(), spouseFam)
        if (personId == Global.indi && person.getParentFamilies(Global.gc).size > 1)
            menu.add(0, -1, 0, R.string.diagram)
        if (personId != Global.indi)
            menu.add(0, 0, 0, R.string.card)
        if (familyLabels[0] != null)
            menu.add(0, 1, 0, familyLabels[0])
        if (familyLabels[1] != null)
            menu.add(0, 2, 0, familyLabels[1])
        menu.add(0, 3, 0, R.string.new_relative)
        if (U.linkablePersons(person))
            menu.add(0, 4, 0, R.string.link_person)
        menu.add(0, 5, 0, R.string.modify)
        if (person.getParentFamilies(Global.gc).isNotEmpty() || person.getSpouseFamilies(Global.gc).isNotEmpty())
            menu.add(0, 6, 0, R.string.unlink)
        menu.add(0, 7, 0, R.string.delete)
        popup?.visibility = View.INVISIBLE
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (item.groupId != 0) return false
        val relatives = arrayOf(getText(R.string.parent), getText(R.string.sibling), getText(R.string.partner), getText(R.string.child))
        val id = item.itemId
        if (id == -1) { // Diagram for fulcrum child in many families
            if (person.getParentFamilies(Global.gc).size > 2) // More than two families
                selectParentFamily(person)
            else completeSelect(person, if (Global.familyNum == 0) 1 else 0) // Two families
        } else if (id == 0) { // Person profile
            Memory.setLeader(person)
            startActivity(Intent(context, ProfileActivity::class.java))
        } else if (id == 1) { // Family as child
            if (personId == Global.indi) { // If fulcrum, opens directly the family
                Memory.setLeader(parentFam)
                startActivity(Intent(context, FamilyActivity::class.java))
            } else U.whichParentsToShow(context, person, 2)
        } else if (id == 2) { // Family as spouse
            U.whichSpousesToShow(context, person)
        } else if (id == 3) { // Link new person
            if (Global.settings.expert) {
                val dialog = NewRelativeDialog(person, parentFam, spouseFam, true, null)
                dialog.show(requireActivity().supportFragmentManager, null)
            } else {
                AlertDialog.Builder(context()).setItems(relatives) { _, selected ->
                    val intent = Intent(context, PersonEditorActivity::class.java)
                        .putExtra(Extra.PERSON_ID, personId)
                        .putExtra(Extra.RELATION, Relation.get(selected))
                    if (U.checkMultiMarriages(intent, context, null)) return@setItems
                    startActivity(intent)
                }.show()
            }
        } else if (id == 4) { // Link existing person
            if (Global.settings.expert) {
                val dialog = NewRelativeDialog(person, parentFam, spouseFam, false, this@DiagramFragment)
                dialog.show(requireActivity().supportFragmentManager, null)
            } else {
                AlertDialog.Builder(context()).setItems(relatives) { _, selected ->
                    val intent = Intent(context, MainActivity::class.java)
                        .putExtra(Choice.PERSON, true)
                        .putExtra(Extra.PERSON_ID, personId)
                        .putExtra(Extra.RELATION, Relation.get(selected))
                    if (U.checkMultiMarriages(intent, context, this@DiagramFragment)) return@setItems
                    choosePersonLauncher.launch(intent)
                }.show()
            }
        } else if (id == 5) { // Edit
            val intent = Intent(context, PersonEditorActivity::class.java)
            intent.putExtra(Extra.PERSON_ID, personId)
            startActivity(intent)
        } else if (id == 6) { // Unlink
            /* TODO to be precise we should use FamilyUtil.unlinkRefs(sfr, sr)
               which removes exactly the single link instead of all links if a person is linked multiple times in the same family */
            val modified = ArrayList<Family>()
            if (parentFam != null) {
                FamilyUtil.unlinkPerson(person, parentFam!!)
                modified.add(parentFam!!)
            }
            if (spouseFam != null) {
                FamilyUtil.unlinkPerson(person, spouseFam!!)
                FamilyUtil.updateSpouseRoles(spouseFam!!)
                modified.add(spouseFam!!)
            }
            val modifiedArray = modified.toTypedArray()
            U.deleteEmptyFamilies(context(), { refreshAll() }, false, *modifiedArray)
            ChangeUtil.updateChangeDate(person)
            TreeUtil.save(true, *modifiedArray)
            refreshAll()
        } else if (id == 7) { // Delete
            confirmDelete(context()) {
                val families = person.delete()
                refreshAll()
                U.deleteEmptyFamilies(context, { refreshAll() }, false, *families)
            }
        } else return false
        return true
    }

    /**
     * Adds the relative who has been chosen in PersonsFragment.
     */
    val choosePersonLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                val modified = PersonEditorActivity.addRelative(
                    data.getStringExtra(Extra.PERSON_ID), // Corresponds to 'personId', which however is annulled in case of a configuration change
                    data.getStringExtra(Extra.RELATIVE_ID),
                    data.getStringExtra(Extra.FAMILY_ID),
                    data.getSerializableExtra(Extra.RELATION) as Relation,
                    data.getStringExtra(Extra.DESTINATION)
                )
                TreeUtil.save(true, *modified)
            }
        }
    }

    /**
     * Prepares present diagram for export as PNG.
     */
    private fun generatePng() {
        binding.diagramWheel.root.visibility = View.VISIBLE
        // Stylizes diagram for print
        printing = true
        for (i in 0 until box.childCount) {
            box.getChildAt(i).invalidate()
        }
        printing = false
        // Starts the export process
        lifecycleScope.launch(Dispatchers.IO) {
            createBitmap(1F)
        }
    }

    /**
     * Recursively generates a bitmap of acceptable size.
     * Exports the bitmap to a temporary PNG file and asks where to save it.
     */
    private suspend fun createBitmap(scale: Float) {
        try {
            // Creates Bitmap with the scaled dimensions
            val bitmap = Bitmap.createBitmap((box.width * scale).toInt(), (box.height * scale).toInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            // Scales also the canvas
            if (scale < 1) {
                val scaleMatrix = Matrix()
                scaleMatrix.setScale(scale, scale, 0F, 0F)
                canvas.setMatrix(scaleMatrix)
            }
            // Draws the content of the view onto the Bitmap
            box.draw(canvas)
            // Saves the Bitmap as a PNG file
            val outputStream = FileOutputStream(File(context().cacheDir, "temp"))
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
            outputStream.close()
        } catch (exception: Exception) {
            generateFail(exception.localizedMessage)
            return
        } catch (error: Error) {
            if (error is OutOfMemoryError) createBitmap(scale * 0.99F)
            else generateFail(error.localizedMessage)
            return
        }
        // Open SAF to choose where to save the PNG file
        FileUtil.openSaf(Global.settings.openTree, FileType.PNG, savePngLauncher)
    }

    /**
     * Exports the present diagram to a temporary PDF file and asks where to save it.
     */
    private fun generatePdf() {
        binding.diagramWheel.root.visibility = View.VISIBLE
        // Stylizes diagram for print
        printing = true
        for (i in 0 until box.childCount) {
            box.getChildAt(i).invalidate()
        }
        printing = false
        lifecycleScope.launch(Dispatchers.IO) {
            delay(50) // Just to wait for previous invalidate to take effect, especially on single-card diagram
            // Creates PDF
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(box.width, box.height, 1).create()
            val page = document.startPage(pageInfo)
            box.draw(page.canvas)
            document.finishPage(page)
            // Writes PDF
            try {
                val outputStream = FileOutputStream(File(context().cacheDir, "temp"))
                document.writeTo(outputStream)
                outputStream.flush()
                outputStream.close()
            } catch (e: Exception) {
                generateFail(e.localizedMessage)
                return@launch
            }
            FileUtil.openSaf(Global.settings.openTree, FileType.PDF, savePdfLauncher)
        }
    }

    /**
     * Bad conclusion of previous two functions.
     */
    private suspend fun generateFail(message: String?) {
        withContext(Dispatchers.Main) {
            message?.let { Toast.makeText(context(), it, Toast.LENGTH_LONG).show() }
            binding.diagramWheel.root.visibility = View.GONE
        }
    }

    /** Copies the temporary PNG file to its final destination. */
    private val savePngLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        finalizeExport(it, FileType.PNG)
    }

    /** Copies the temporary PDF file to its final destination. */
    private val savePdfLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        finalizeExport(it, FileType.PDF)
    }

    private fun finalizeExport(result: ActivityResult, fileType: FileType) {
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                File(context().cacheDir, "temp").inputStream().use { input ->
                    context().contentResolver.openOutputStream(uri).use { output ->
                        if (output != null) {
                            input.copyTo(output)
                            val message = if (fileType == FileType.PNG) R.string.png_exported_ok else R.string.pdf_exported_ok
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else Toast.makeText(context, R.string.cant_understand_uri, Toast.LENGTH_LONG).show()
        } // No need to handle RESULT_CANCELED
        binding.diagramWheel.root.visibility = View.GONE
    }
}
