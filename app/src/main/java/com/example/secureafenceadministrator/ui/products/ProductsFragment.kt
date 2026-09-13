package com.example.secureafenceadministrator.ui.products

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.example.secureafenceadministrator.R
import com.example.secureafenceadministrator.data.model.Product
import com.example.secureafenceadministrator.data.network.ApiClient
import com.example.secureafenceadministrator.data.network.SessionManager
import com.example.secureafenceadministrator.databinding.FragmentProductsBinding
import com.example.secureafenceadministrator.ui.common.GenericAdapter
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

class ProductsFragment : Fragment() {

    private var _binding: FragmentProductsBinding? = null
    private val binding get() = _binding!!
    
    private var selectedImageUri: Uri? = null
    private var ivPreview: ImageView? = null
    private var currentImageUrl: String? = null

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            ivPreview?.load(it)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProductsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerViewProducts.layoutManager = LinearLayoutManager(requireContext())
        
        binding.fabAddProduct.setOnClickListener {
            showProductDialog(null)
        }
        
        loadProducts()
    }

    private fun loadProducts() {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return

        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.getAdminProducts("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    val products = response.body()!!
                    val adapter = GenericAdapter(
                        products,
                        titleProvider = { it.name },
                        subtitleProvider = { "Buy: $${it.salePrice} | Rent: $${it.rentalPriceMonthly}/mo | Stock: ${it.inStock} ${if (it.type == "panel" || it.type == "accessory") "LF" else "units"}" },
                        statusProvider = { if (it.suspended) "SUSPENDED" else "ACTIVE" },
                        descriptionProvider = { it.description },
                        imageProvider = { it.image },
                        onItemClick = { showProductDetails(it) }
                    )
                    binding.recyclerViewProducts.adapter = adapter
                } else {
                    Toast.makeText(context, "Failed to load products", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showProductDetails(product: Product) {
        val context = context ?: return
        val builder = AlertDialog.Builder(context)
        builder.setTitle(product.name)
        
        val unitLabel = if (product.type == "panel" || product.type == "accessory") "LF" else "units"
        val details = """
            Category: ${product.category}
            Type: ${product.type}
            Sale Price: $${product.salePrice}
            Rental Price: $${product.rentalPriceMonthly}/mo
            Stock: ${product.inStock} $unitLabel
            Rented Out: ${product.rentedCount} $unitLabel
            Rental Catalog: ${if (product.isRental) "Yes" else "No"}
            Purchase Catalog: ${if (product.isPurchase) "Yes" else "No"}
            Status: ${if (product.suspended) "Suspended" else "Active"}
            
            Description: ${product.description}
        """.trimIndent()
        
        builder.setMessage(details)
        
        builder.setPositiveButton("Edit") { _, _ -> showProductDialog(product) }
        builder.setNegativeButton("Delete") { _, _ -> confirmDelete(product) }
        builder.setNeutralButton("Toggle Status") { _, _ -> toggleSuspension(product) }
        builder.show()
    }

    private fun toggleSuspension(product: Product) {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return
        val updatedProduct = product.copy(suspended = !product.suspended)
        
        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.updateProduct("Bearer $token", product.id!!, updatedProduct)
                if (response.isSuccessful) {
                    Toast.makeText(context, "Product status updated", Toast.LENGTH_SHORT).show()
                    loadProducts()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDelete(product: Product) {
        val context = context ?: return
        AlertDialog.Builder(context)
            .setTitle("Delete Product")
            .setMessage("Are you sure you want to delete ${product.name}?")
            .setPositiveButton("Yes") { _, _ -> deleteProduct(product) }
            .setNegativeButton("No", null)
            .show()
    }

    private fun deleteProduct(product: Product) {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return
        
        lifecycleScope.launch {
            try {
                val response = ApiClient.instance.deleteProduct("Bearer $token", product.id!!)
                if (response.isSuccessful) {
                    Toast.makeText(context, "Product deleted", Toast.LENGTH_SHORT).show()
                    loadProducts()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showProductDialog(product: Product?) {
        val context = context ?: return
        val builder = AlertDialog.Builder(context)
        builder.setTitle(if (product == null) "Add Product" else "Edit Product")

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_product, null)
        val etName = view.findViewById<EditText>(R.id.et_product_name)
        val etSalePrice = view.findViewById<EditText>(R.id.et_sale_price)
        val etRentalPrice = view.findViewById<EditText>(R.id.et_rental_price)
        val etStock = view.findViewById<EditText>(R.id.et_stock)
        val etDescription = view.findViewById<EditText>(R.id.et_description)
        val etSpecs = view.findViewById<EditText>(R.id.et_specs)
        val cbIsRental = view.findViewById<CheckBox>(R.id.cb_is_rental)
        val cbIsPurchase = view.findViewById<CheckBox>(R.id.cb_is_purchase)
        val spType = view.findViewById<Spinner>(R.id.sp_product_type)
        ivPreview = view.findViewById(R.id.iv_product_preview)
        val btnUpload = view.findViewById<Button>(R.id.btn_upload_image)

        val types = arrayOf("panel", "stand", "clip", "gate", "accessory")
        spType.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, types)

        selectedImageUri = null
        currentImageUrl = product?.image

        product?.let {
            etName.setText(it.name)
            etSalePrice.setText(it.salePrice.toString())
            etRentalPrice.setText(it.rentalPriceMonthly.toString())
            etStock.setText(it.inStock.toString())
            etDescription.setText(it.description)
            etSpecs.setText(it.specs)
            cbIsRental.isChecked = it.isRental
            cbIsPurchase.isChecked = it.isPurchase
            val typeIndex = types.indexOf(it.type)
            if (typeIndex >= 0) spType.setSelection(typeIndex)
            
            if (!it.image.isNullOrEmpty()) {
                val fullUrl = if (it.image.startsWith("/")) "https://secure-a-fence-backend.onrender.com${it.image}" else it.image
                ivPreview?.load(fullUrl)
            }
        }

        btnUpload.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        builder.setView(view)
        builder.setPositiveButton("Save") { _, _ ->
            val pName = etName.text.toString()
            if (pName.isEmpty()) return@setPositiveButton

            val imageToUpload = selectedImageUri
            val initialImage = currentImageUrl
            selectedImageUri = null
            currentImageUrl = null

            lifecycleScope.launch {
                val selectedType = spType.selectedItem.toString()
                var finalImageUrl = initialImage ?: when (selectedType) {
                    "stand" -> "/assets/stand.svg"
                    "clip" -> "/assets/clip.svg"
                    "accessory" -> "/assets/privacy_screen.svg"
                    else -> "/assets/panel.svg"
                }
                
                imageToUpload?.let { uri ->
                    val uploadedUrl = uploadImage(uri)
                    if (!uploadedUrl.isNullOrEmpty()) {
                        finalImageUrl = uploadedUrl
                    }
                }

                val newProduct = Product(
                    id = product?.id,
                    name = pName,
                    category = "sales",
                    type = selectedType,
                    salePrice = etSalePrice.text.toString().toDoubleOrNull() ?: 0.0,
                    rentalPriceMonthly = etRentalPrice.text.toString().toDoubleOrNull() ?: 0.0,
                    inStock = etStock.text.toString().toIntOrNull() ?: 0,
                    rentedCount = product?.rentedCount ?: 0,
                    description = etDescription.text.toString(),
                    image = finalImageUrl,
                    specs = etSpecs.text.toString(),
                    suspended = product?.suspended ?: false,
                    isRental = cbIsRental.isChecked,
                    isPurchase = cbIsPurchase.isChecked
                )
                saveProduct(newProduct)
            }
        }
        builder.setNegativeButton("Cancel") { _, _ ->
            selectedImageUri = null
            currentImageUrl = null
        }
        builder.show()
    }

    private suspend fun uploadImage(uri: Uri): String? {
        val context = context ?: return null
        val token = SessionManager.getToken(context) ?: return null
        
        try {
            val file = uriToFile(uri) ?: return null
            val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("image", file.name, requestFile)
            
            val response = ApiClient.instance.uploadProductImage("Bearer $token", body)
            if (response.isSuccessful && response.body() != null) {
                return response.body()!!["imageUrl"] as? String
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun uriToFile(uri: Uri): File? {
        val context = context ?: return null
        val contentResolver = context.contentResolver
        val tempFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
        
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            return tempFile
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun saveProduct(product: Product) {
        val context = context ?: return
        val token = SessionManager.getToken(context) ?: return
        
        lifecycleScope.launch {
            try {
                val response = if (product.id == null) {
                    ApiClient.instance.createProduct("Bearer $token", product)
                } else {
                    ApiClient.instance.updateProduct("Bearer $token", product.id, product)
                }
                
                if (response.isSuccessful) {
                    Toast.makeText(context, "Product saved", Toast.LENGTH_SHORT).show()
                    loadProducts()
                } else {
                    Toast.makeText(context, "Failed to save product", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
