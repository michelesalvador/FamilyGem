package app.familygem

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.widget.SwitchCompat
import androidx.core.text.TextUtilsCompat
import app.familygem.databinding.DiagramSettingsActivityBinding
import java.util.Locale

/**
 * Here user can set some dimensions of the diagram.
 */
class DiagramSettingsActivity : BaseActivity() {

    private lateinit var binding: DiagramSettingsActivityBinding
    private lateinit var ancestors: SeekBar
    private lateinit var uncles: SeekBar
    private lateinit var partners: SwitchCompat
    private lateinit var descendants: SeekBar
    private lateinit var siblings: SeekBar
    private lateinit var cousins: SeekBar
    private lateinit var numbers: SwitchCompat
    private lateinit var duplicateLines: SwitchCompat
    private lateinit var indicator: LinearLayout
    private lateinit var animator: AnimatorSet
    private var leftToRight = true
    private var maySave = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DiagramSettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        indicator = binding.diagramSettingsIndicator
        leftToRight = TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_LTR
        // Number of ancestors
        ancestors = binding.diagramSettingsAncestors
        ancestors.progress = decode(Global.settings.diagram.ancestors)
        ancestors.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                if (i < uncles.progress) {
                    uncles.progress = i
                }
                if (i == 0 && siblings.progress > 0) {
                    siblings.progress = 0
                }
                if (i == 0 && cousins.progress > 0) {
                    cousins.progress = 0
                }
                moveIndicator(seekBar)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                maySave = true
            }
        })
        // Number of uncles and great-uncles
        uncles = binding.diagramSettingsGreatUncles
        uncles.progress = decode(Global.settings.diagram.uncles)
        uncles.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                if (i > ancestors.progress) {
                    ancestors.progress = i
                }
                moveIndicator(seekBar)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                if (seekBar.progress > 0 && cousins.progress == 0) {
                    cousins.progress = 1
                }
                maySave = true
            }
        })
        // Displays partners
        partners = binding.diagramSettingsPartners
        partners.isChecked = Global.settings.diagram.spouses
        partners.setOnCheckedChangeListener { _, _ ->
            maySave = true
        }
        // Number of descendants
        descendants = binding.diagramSettingsDescendants
        descendants.progress = decode(Global.settings.diagram.descendants)
        descendants.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                moveIndicator(seekBar)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                maySave = true
            }
        })
        // Number of siblings and nephews
        siblings = binding.diagramSettingsSiblingsNephews
        siblings.progress = decode(Global.settings.diagram.siblings)
        siblings.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                if (i > 0 && ancestors.progress == 0) {
                    ancestors.progress = 1
                }
                moveIndicator(seekBar)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                maySave = true
            }
        })
        // Number of uncles and cousins
        cousins = binding.diagramSettingsUnclesCousins
        cousins.progress = decode(Global.settings.diagram.cousins)
        cousins.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                if (i > 0 && ancestors.progress == 0) {
                    ancestors.progress = 1
                }
                moveIndicator(seekBar)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                if (seekBar.progress > 0 && uncles.progress == 0) {
                    uncles.progress = 1
                }
                maySave = true
            }
        })
        // Displays little numbers
        numbers = binding.diagramSettingsNumbers
        numbers.isChecked = Global.settings.diagram.numbers
        numbers.setOnCheckedChangeListener { _, _ ->
            maySave = true
        }
        // Displays duplicates lines
        duplicateLines = binding.diagramSettingsDuplicateLines
        duplicateLines.isChecked = Global.settings.diagram.duplicates
        duplicateLines.setOnCheckedChangeListener { _, _ ->
            maySave = true
        }
        val alphaIn = ObjectAnimator.ofFloat(indicator, View.ALPHA, 1F)
        alphaIn.duration = 0
        val alphaOut = ObjectAnimator.ofFloat(indicator, View.ALPHA, 1F, 0F)
        alphaOut.startDelay = 2000
        alphaOut.duration = 500
        animator = AnimatorSet()
        animator.play(alphaIn)
        animator.play(alphaOut).after(alphaIn)
        indicator.alpha = 0F
    }

    private fun moveIndicator(seekBar: SeekBar) {
        val progress = seekBar.progress
        binding.diagramSettingsIndicatorText.text = convert(progress).toString()
        val width = seekBar.width - seekBar.paddingLeft - seekBar.paddingRight
        indicator.x = if (leftToRight) seekBar.x + seekBar.paddingLeft + width / 9F * progress - indicator.width / 2
        else seekBar.x + seekBar.width - seekBar.paddingRight - width / 9F * progress - indicator.width / 2
        indicator.y = seekBar.y - indicator.height
        animator.cancel()
        animator.start()
    }

    /**
     * Value from settings scale (1 2 3 4 5 10 20 50 100) to linear scale (1 2 3 4 5 6 7 8 9).
     */
    private fun decode(i: Int): Int {
        return when (i) {
            100 -> 9
            50 -> 8
            20 -> 7
            10 -> 6
            else -> i
        }
    }

    /**
     * Value from linear scale to settings scale.
     */
    private fun convert(i: Int): Int {
        return when (i) {
            6 -> 10
            7 -> 20
            8 -> 50
            9 -> 100
            else -> i
        }
    }

    override fun onPause() {
        super.onPause()
        if (maySave) {
            Global.settings.apply {
                diagram.ancestors = convert(ancestors.progress)
                diagram.uncles = convert(uncles.progress)
                diagram.spouses = partners.isChecked
                diagram.descendants = convert(descendants.progress)
                diagram.siblings = convert(siblings.progress)
                diagram.cousins = convert(cousins.progress)
                diagram.numbers = numbers.isChecked
                diagram.duplicates = duplicateLines.isChecked
                save()
            }
            Global.edited = true
            maySave = false
        }
    }

    // Toolbar back arrow
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}
