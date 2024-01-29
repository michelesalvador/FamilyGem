package app.familygem.main

import android.content.Context
import android.content.Intent
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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.TextUtilsCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import app.familygem.DiagramSettingsActivity
import app.familygem.F
import app.familygem.GedcomDateConverter
import app.familygem.Global
import app.familygem.Memory
import app.familygem.NewRelativeDialog
import app.familygem.PersonEditorActivity
import app.familygem.ProfileActivity
import app.familygem.R
import app.familygem.U
import app.familygem.constant.Choice
import app.familygem.constant.Extra
import app.familygem.constant.Gender
import app.familygem.constant.Relation
import app.familygem.databinding.DiagramFragmentBinding
import app.familygem.detail.FamilyActivity
import app.familygem.util.ChangeUtil
import app.familygem.util.FileUtil
import app.familygem.util.TreeUtil
import app.familygem.util.getFamilyLabels
import graph.gedcom.Bond
import graph.gedcom.CurveLine
import graph.gedcom.Graph
import graph.gedcom.Line
import graph.gedcom.Metric
import graph.gedcom.PersonNode
import graph.gedcom.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.folg.gedcom.model.Family
import org.folg.gedcom.model.Media
import org.folg.gedcom.model.Person
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
    private var density = 0f
    private var lineStroke = 0 // Lines thickness, in pixels
    private var lineDash = 0 // Dashed lines interval
    private var glowSpace = 0 // Space to display glow around cards
    private var popup: View? = null // Suggestion balloon
    private var printPDF = false // We are exporting a PDF
    private val leftToRight = TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault()) == ViewCompat.LAYOUT_DIRECTION_LTR
    private val graphicNodes: MutableList<GraphicMetric> = ArrayList()
    private val loadingImages: MutableList<Pair<Media, ImageView>> = ArrayList()
    lateinit var choosePersonLauncher: ActivityResultLauncher<Intent>

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
            if (Global.gc.people.size > 0) menu.add(0, 1, 0, R.string.export_pdf)
            settings.show()
            settings.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    // Diagram settings
                    0 -> startActivity(Intent(context(), DiagramSettingsActivity::class.java))
                    // Export PDF
                    1 -> F.saveDocument(null, this, Global.settings.openTree, "application/pdf", "pdf", 903)
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
        }
        moveLayout = binding.diagramFrame
        moveLayout.leftToRight = leftToRight
        moveLayout.graph = graph
        box = binding.diagramBox

        // Adds the relative who has been chosen in PersonsFragment
        choosePersonLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                val data = result.data
                if (data != null) {
                    val modified = PersonEditorActivity.addRelative(
                            data.getStringExtra(Extra.PERSON_ID), // Corresponds to 'personId', which however is annulled in case of a configuration change
                            data.getStringExtra(Extra.RELATIVE_ID),
                            data.getStringExtra(Extra.FAMILY_ID),
                            data.getSerializableExtra(Extra.RELATION) as Relation,
                            data.getStringExtra(Extra.DESTINATION))
                    TreeUtil.save(true, *modified)
                }
            }
        }
        return binding.root
    }

    private fun context(): Context {
        return context ?: Global.context
    }

    // Called after choosePersonLauncher
    override fun onResume() {
        // Reasons why we should continue, especially things that have changed
        mayShow = mayShow || graph.fulcrum == null || graph.fulcrum.id != Global.indi || graph.whichFamily != Global.familyNum
        super.onResume() // Must stay at the end to apply mayShow value
    }

    override fun showContent() {
        startDiagram()
    }

    /**
     * Identifies the fulcrum person to start from, shows any button 'Add the first person' or starts the diagram.
     */
    private fun startDiagram() {
        box.removeAllViews()
        // Finds fulcrum
        val ids = arrayOf(Global.indi, Global.settings.currentTree.root, U.findRootId(Global.gc))
        var fulcrum: Person? = null
        for (id in ids) {
            fulcrum = Global.gc.getPerson(id)
            if (fulcrum != null) break
        }
        // Empty diagram
        if (fulcrum == null) {
            val button = LayoutInflater.from(context()).inflate(R.layout.diagram_button, null)
            button.findViewById<View>(R.id.diagram_new).setOnClickListener {
                startActivity(Intent(context(), PersonEditorActivity::class.java))
            }
            SuggestionBalloon(context(), button, R.string.new_person)
            if (!Global.settings.expert) binding.diagramOptions.visibility = View.GONE
        } else {
            binding.diagramWheel.root.visibility = View.VISIBLE
            lifecycleScope.launch(Dispatchers.Default) {
                Global.indi = fulcrum.id // Confirms it just in case
                graph.setGedcom(Global.gc)
                        .maxAncestors(Global.settings.diagram.ancestors)
                        .maxGreatUncles(Global.settings.diagram.uncles)
                        .displaySpouses(Global.settings.diagram.spouses)
                        .maxDescendants(Global.settings.diagram.descendants)
                        .maxSiblingsNephews(Global.settings.diagram.siblings)
                        .maxUnclesCousins(Global.settings.diagram.cousins)
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
    private suspend fun drawDiagram() {
        graphicNodes.clear()
        loadingImages.clear()
        // Place various type of graphic nodes in 'graphicNodes' list
        for (personNode in graph.personNodes) {
            if (personNode.person.id == Global.indi && !personNode.isFulcrumNode)
                graphicNodes.add(Asterisk(context(), personNode))
            else if (personNode.mini)
                graphicNodes.add(GraphicMiniCard(context(), personNode))
            else graphicNodes.add(GraphicPerson(context(), personNode))
        }
        // First layout of cards
        graphicNodes.forEach {
            it.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.AT_MOST)
            it.layout(0, 0, it.measuredWidth, it.measuredHeight)
        }
        // Loads images
        withContext(Dispatchers.Main) {
            loadingImages.forEach {
                FileUtil.showImage(it.first, it.second)
            }
        }
        // Waits for images to be loaded
        if (loadingImages.size > 0) {
            do {
                delay(200)
                var imageToLoad = false
                for (it in loadingImages) {
                    if (it.second.tag != R.id.tag_object) {
                        imageToLoad = true
                        break
                    }
                }
            } while (imageToLoad)
        }
        // Only one person in the diagram
        if (graphicNodes.size == 1 && !printPDF) {
            withContext(Dispatchers.Main) {
                // Puts the card under the suggestion balloon
                val singleNode = graphicNodes[0]
                singleNode.id = R.id.tag_fulcrum
                val popupLayout: ConstraintLayout = SuggestionBalloon(context(), singleNode, R.string.long_press_menu)
                // Adds the glow to the fulcrum card
                if (fulcrumView != null) {
                    box.post {
                        val glowParams = ConstraintLayout.LayoutParams(
                                singleNode.width + glowSpace * 2, singleNode.height + glowSpace * 2)
                        glowParams.topToTop = R.id.tag_fulcrum
                        glowParams.bottomToBottom = R.id.tag_fulcrum
                        glowParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                        glowParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                        fulcrumView!!.metric.width = toDp(singleNode.width.toFloat())
                        fulcrumView!!.metric.height = toDp(singleNode.height.toFloat())
                        popupLayout.addView(FulcrumGlow(context), 0, glowParams)
                    }
                }
                binding.diagramWheel.root.visibility = View.GONE
            }
        } else { // Two or more persons in the diagram or PDF print
            // Gets the dimensions of each node converting from pixel to dip
            graphicNodes.forEach {
                // Second measurement to get final card size
                it.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
                it.metric.width = toDp(it.measuredWidth.toFloat())
                it.metric.height = toDp(it.measuredHeight.toFloat())
            }
            graph.initNodes() // Initializes nodes and lines
            // Adds bond nodes
            for (bond in graph.bonds) {
                graphicNodes.add(GraphicBond(context(), bond))
            }
            graph.placeNodes() // Calculates final position
            // Other diagram elements
            withContext(Dispatchers.Main) {
                // Adds the lines
                lines = Lines(context(), graph.lines, false)
                box.addView(lines, 0)
                backLines = Lines(context(), graph.backLines, true)
                box.addView(backLines, 0)
                // Adds the glow
                val fulcrumNode = fulcrumView!!.metric as PersonNode
                val glowParams = RelativeLayout.LayoutParams(
                        toPx(fulcrumNode.width) + glowSpace * 2, toPx(fulcrumNode.height) + glowSpace * 2)
                glowParams.rightMargin = -glowSpace
                glowParams.bottomMargin = -glowSpace
                box.addView(FulcrumGlow(context()), 0, glowParams)
                displaceDiagram()
            }
        }
    }

    /**
     * Updates visible position of nodes and lines.
     */
    private fun displaceDiagram() {
        // Position of the nodes from dips to pixels
        graphicNodes.forEach {
            val params = it.layoutParams as RelativeLayout.LayoutParams
            if (leftToRight) params.leftMargin = toPx(it.metric.x)
            else params.rightMargin = toPx(it.metric.x)
            params.topMargin = toPx(it.metric.y)
            box.addView(it)
        }
        // The glow follows fulcrum
        val glowParams = glow!!.layoutParams as RelativeLayout.LayoutParams
        if (leftToRight) glowParams.leftMargin = toPx(fulcrumView!!.metric.x) - glowSpace
        else glowParams.rightMargin = toPx(fulcrumView!!.metric.x) - glowSpace
        glowParams.topMargin = toPx(fulcrumView!!.metric.y) - glowSpace
        moveLayout.childWidth = toPx(graph.width) + box.paddingStart * 2
        moveLayout.childHeight = toPx(graph.height) + box.paddingTop * 2
        // Updates lines
        lines.invalidate()
        backLines.invalidate()
        binding.diagramWheel.root.visibility = View.GONE
        // Pans to fulcrum
        val scale = moveLayout.minimumScale()
        val padding = box.paddingTop * scale
        moveLayout.panTo((if (leftToRight) toPx(fulcrumView!!.metric.centerX()) * scale - moveLayout.width / 2 + padding
        else moveLayout.width / 2 - toPx(fulcrumView!!.metric.centerX()) * scale - padding).toInt(),
                (toPx(fulcrumView!!.metric.centerY()) * scale - moveLayout.height / 2 + padding).toInt())
    }

    /**
     * Updates both diagram and main menu.
     */
    private fun refreshAll() {
        startDiagram()
        (requireActivity() as MainActivity).furnishMenu()
    }

    /**
     * Puts a view under the suggestion balloon.
     */
    inner class SuggestionBalloon(context: Context, childView: View, suggestion: Int) : ConstraintLayout(context) {
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
            if (printPDF) {
                popup!!.visibility = GONE
                glow?.visibility = GONE
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
            canvas.drawRect((glowSpace - extend).toFloat(), (glowSpace - extend).toFloat(),
                    (toPx(fulcrumView!!.metric.width) + glowSpace + extend).toFloat(),
                    (toPx(fulcrumView!!.metric.height) + glowSpace + extend).toFloat(), paint)
        }

        override fun invalidate() {
            if (printPDF) {
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
            if (Gender.isMale(person)) border.setBackgroundResource(R.drawable.casella_bordo_maschio)
            else if (Gender.isFemale(person)) border.setBackgroundResource(R.drawable.casella_bordo_femmina)
            background = view.findViewById(R.id.card_background)
            if (personNode.isFulcrumNode) {
                background.setBackgroundResource(R.drawable.casella_sfondo_evidente)
                fulcrumView = this
            } else if (personNode.acquired) {
                background.setBackgroundResource(R.drawable.casella_sfondo_sposo)
            }
            val imageView = view.findViewById<ImageView>(R.id.card_picture)
            val media = FileUtil.selectMainImage(person, imageView, show = false)
            if (media != null) loadingImages.add(Pair(media, imageView))
            view.findViewById<TextView>(R.id.card_name).text = U.properName(person, true)
            val titleView = view.findViewById<TextView>(R.id.card_title)
            val title = U.titolo(person)
            if (title.isEmpty()) titleView.visibility = GONE else titleView.text = title
            val dataView = view.findViewById<TextView>(R.id.card_data)
            val data = U.twoDates(person, true)
            if (data.isEmpty()) dataView.visibility = GONE else dataView.text = data
            if (!U.isDead(person)) view.findViewById<View>(R.id.card_mourn).visibility = GONE
            registerForContextMenu(this)
            setOnClickListener {
                if (person.id == Global.indi) {
                    Memory.setLeader(person)
                    startActivity(Intent(getContext(), ProfileActivity::class.java))
                } else {
                    clickCard(person)
                }
            }
        }

        override fun invalidate() {
            // Changes background color for PDF export
            if (printPDF && (metric as PersonNode).acquired) {
                background.setBackgroundResource(R.drawable.casella_sfondo_sposo_stampa)
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
            if (printPDF && hearth != null) {
                hearth!!.setBackgroundResource(R.drawable.diagram_hearth_print)
            }
        }
    }

    /**
     * Little ancestry or progeny card.
     */
    inner class GraphicMiniCard(context: Context, personNode: PersonNode) : GraphicMetric(context, personNode) {
        var layout: RelativeLayout? = null

        init {
            val miniCard = layoutInflater.inflate(R.layout.diagram_minicard, this, true)
            val miniCardText = miniCard.findViewById<TextView>(R.id.minicard_text)
            miniCardText.text = if (personNode.amount > 100) "100+" else personNode.amount.toString()
            val sex = Gender.getGender(personNode.person)
            if (sex == Gender.MALE) miniCardText.setBackgroundResource(R.drawable.casella_bordo_maschio)
            else if (sex == Gender.FEMALE) miniCardText.setBackgroundResource(R.drawable.casella_bordo_femmina)
            if (personNode.acquired) {
                layout = miniCard.findViewById(R.id.minicard)
                layout!!.setBackgroundResource(R.drawable.casella_sfondo_sposo)
            }
            miniCard.setOnClickListener { clickCard(personNode.person) }
        }

        override fun invalidate() {
            if (printPDF && layout != null) {
                layout!!.setBackgroundResource(R.drawable.casella_sfondo_sposo_stampa)
            }
        }
    }

    /**
     * Replacement for another person who is actually fulcrum.
     */
    inner class Asterisk(context: Context, personNode: PersonNode) : GraphicMetric(context, personNode) {
        init {
            layoutInflater.inflate(R.layout.diagram_asterisk, this, true)
            registerForContextMenu(this)
            setOnClickListener {
                Memory.setLeader(personNode.person)
                startActivity(Intent(getContext(), ProfileActivity::class.java))
            }
        }
    }

    /**
     * Generates the view of lines connecting the cards.
     */
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
            paint.color = ResourcesCompat.getColor(resources, if (printPDF) R.color.diagram_lines_print else R.color.diagram_lines_screen, null)
            paths.clear() // In case of PDF print
            val width = toPx(graph.width).toFloat()
            // Put the lines in one or more paths
            for ((pathNum, lineGroup) in lineGroups.withIndex()) {
                if (pathNum >= paths.size) paths.add(Path())
                val path = paths[pathNum]
                for (line in lineGroup) {
                    var x1 = toPx(line.x1).toFloat()
                    val y1 = toPx(line.y1).toFloat()
                    var x2 = toPx(line.x2).toFloat()
                    val y2 = toPx(line.y2).toFloat()
                    if (!leftToRight) {
                        x1 = width - x1
                        x2 = width - x2
                    }
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

    private fun clickCard(person: Person) {
        if (TreeUtil.isGlobalGedcomOk { selectParentFamily(person) }) selectParentFamily(person)
    }

    /**
     * Asks which family to display in the diagram if fulcrum has many parent families.
     */
    private fun selectParentFamily(fulcrum: Person?) {
        val families = fulcrum!!.getParentFamilies(Global.gc)
        if (families.size > 1) {
            AlertDialog.Builder(context()).setTitle(R.string.which_family)
                    .setItems(U.elencoFamiglie(families)) { _, which: Int -> completeSelect(fulcrum, which) }.show()
        } else {
            completeSelect(fulcrum, 0)
        }
    }

    /**
     * Completes above function.
     */
    private fun completeSelect(fulcrum: Person, whichFamily: Int) {
        Global.indi = fulcrum.id
        Global.familyNum = whichFamily
        box.removeAllViews()
        binding.diagramWheel.root.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.Default) {
            graph.showFamily(Global.familyNum)
            graph.startFrom(fulcrum)
            drawDiagram()
        }
    }

    private fun toDp(pixels: Float): Float {
        return pixels / density
    }

    private fun toPx(dips: Float): Int {
        return (dips * density + 0.5f).toInt()
    }

    private lateinit var person: Person
    private lateinit var personId: String
    private var parentFam: Family? = null // Displayed family in which the person is child
    private var spouseFam: Family? = null // Selected family in which the person is spouse

    override fun onCreateContextMenu(menu: ContextMenu, vista: View, info: ContextMenu.ContextMenuInfo?) {
        lateinit var personNode: PersonNode
        if (vista is GraphicPerson) personNode = vista.metric as PersonNode
        else if (vista is Asterisk) personNode = vista.metric as PersonNode
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
        val relatives = arrayOf(getText(R.string.parent), getText(R.string.sibling),
                getText(R.string.partner), getText(R.string.child))
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
            U.whichSpousesToShow(context, person, null)
        } else if (id == 3) { // Link new person
            if (Global.settings.expert) {
                val dialog: DialogFragment = NewRelativeDialog(person, parentFam, spouseFam, true, null)
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
                val dialog: DialogFragment = NewRelativeDialog(person, parentFam, spouseFam, false, this@DiagramFragment)
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
            /* TODO to be precise we should use FamilyActivity.disconnect(sfr, sr)
               which removes exactly the single link instead of all links if a person is linked multiple times in the same family
             */
            val modified: MutableList<Family> = ArrayList()
            if (parentFam != null) {
                FamilyActivity.disconnect(personId, parentFam)
                modified.add(parentFam!!)
            }
            if (spouseFam != null) {
                FamilyActivity.disconnect(personId, spouseFam)
                modified.add(spouseFam!!)
            }
            val modifiedArray = modified.toTypedArray()
            U.controllaFamiglieVuote(context(), { refreshAll() }, false, *modifiedArray)
            ChangeUtil.updateChangeDate(person)
            TreeUtil.save(true, *modifiedArray)
            refreshAll()
        } else if (id == 7) { // Delete
            AlertDialog.Builder(context()).setMessage(R.string.really_delete_person)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        val families = PersonsFragment.deletePerson(context, personId)
                        refreshAll()
                        U.controllaFamiglieVuote(context, { refreshAll() }, false, *families)
                    }.setNeutralButton(R.string.cancel, null).show()
        } else return false
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == AppCompatActivity.RESULT_OK) {
            // Exports diagram to PDF
            if (requestCode == 903) {
                // Stylizes diagram for print
                printPDF = true
                for (i in 0 until box.childCount) {
                    box.getChildAt(i).invalidate()
                }
                fulcrumView!!.findViewById<View>(R.id.card_background).setBackgroundResource(R.drawable.casella_sfondo_base)
                // Creates PDF
                val document = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(box.width, box.height, 1).create()
                val page = document.startPage(pageInfo)
                box.draw(page.canvas)
                document.finishPage(page)
                printPDF = false
                // Writes PDF
                val uri = data!!.data
                try {
                    val out = context().contentResolver.openOutputStream(uri!!)
                    document.writeTo(out)
                    out!!.flush()
                    out.close()
                } catch (e: Exception) {
                    Toast.makeText(context, e.localizedMessage, Toast.LENGTH_LONG).show()
                    return
                }
                Toast.makeText(context, R.string.pdf_exported_ok, Toast.LENGTH_LONG).show()
            }
        }
    }
}
