package app.familygem.purchase

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import app.familygem.Global
import app.familygem.R
import app.familygem.databinding.PremiumFragmentBinding
import app.familygem.util.Util

/**
 * Layout manager of the Premium advertisement and Premium banner.
 */
class PremiumFragment : Fragment(R.layout.premium_fragment) {

    private lateinit var binding: PremiumFragmentBinding
    private val model by viewModels<PremiumViewModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = PremiumFragmentBinding.inflate(inflater, container, false)
        if (Global.settings.premium) model.show.value = PremiumViewModel.Show.ACTIVATED
        else model.show.value = PremiumViewModel.Show.ADVERTISEMENT
        binding.premiumMore.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.familygem.app/premium")))
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        model.message.observe(viewLifecycleOwner) {
            if (it is Int) Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            else Toast.makeText(context, it as String, Toast.LENGTH_LONG).show()
        }
        model.show.observe(viewLifecycleOwner) {
            // Displays product available to purchase
            if (it == PremiumViewModel.Show.ADVERTISEMENT) {
                binding.apply {
                    premiumAdvertisement.visibility = View.VISIBLE
                    premiumSubscribed.visibility = View.GONE
                }
            } // Displays the banner of Premium activated
            else if (it == PremiumViewModel.Show.ACTIVATED) {
                if (activity is PurchaseActivity) { // Back to previous activity
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                } else { // Displays Premium banner
                    binding.apply {
                        premiumAdvertisement.visibility = View.GONE
                        premiumSubscribed.visibility = View.VISIBLE
                    }
                }
            }
        }
        model.status.observe(viewLifecycleOwner) {
            // Re-enables the get Premium button
            if (it == PremiumViewModel.Status.REACTIVATE) {
                binding.premiumButton.isEnabled = true
            } // Any error
            else if (it == PremiumViewModel.Status.ERROR) {
                binding.premiumWheel.visibility = View.GONE
            }
        }
        // Displays the product details on Premium advertisement
        model.productDetails.observe(viewLifecycleOwner) {
            if (it != null) {
                binding.apply {
                    premiumTitle.text = it.name
                    premiumWheel.visibility = View.GONE
                    premiumDescription.text = it.description.replace(" \n", " ").replace(". ", ".\n")
                    premiumDescription.visibility = View.VISIBLE
                    premiumPrice.text = it.oneTimePurchaseOfferDetails!!.formattedPrice
                    premiumOffer.visibility = View.VISIBLE
                    premiumButton.isEnabled = true
                    premiumButton.setOnClickListener { button ->
                        button.isEnabled = false
                        model.buyPremium(requireActivity())
                    }
                }
            }
        }
        // Activates the delete button on Premium banner
        model.purchaseToken.observe(viewLifecycleOwner) {
            if (it != null) {
                binding.apply {
                    premiumDelete.isEnabled = true
                    premiumDelete.setOnClickListener { button ->
                        Util.confirmDelete(requireContext()) {
                            button.isEnabled = false
                            model.consumePurchase()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        model.finish()
    }
}
