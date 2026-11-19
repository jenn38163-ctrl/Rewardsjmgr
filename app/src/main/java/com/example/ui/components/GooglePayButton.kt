package com.example.ui.components

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.wallet.*

@Composable
fun GooglePayButton(
    amount: String,
    currencyCode: String = "USD",
    onPaymentSuccess: (paymentToken: String) -> Unit,
    onPaymentFailed: (error: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val paymentsClient = remember {
        Wallet.getPaymentsClient(
            context,
            Wallet.WalletOptions.Builder()
                .setEnvironment(WalletConstants.ENVIRONMENT_TEST)
                .build()
        )
    }

    val paymentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { intent ->
                PaymentData.getFromIntent(intent)?.let { paymentData ->
                    val token = paymentData.toJson()
                    onPaymentSuccess(token)
                } ?: onPaymentFailed("Failed to parse PaymentData")
            }
        } else if (result.resultCode == Activity.RESULT_CANCELED) {
            onPaymentFailed("Payment cancelled by user")
        } else {
            onPaymentFailed("Payment failed with result code ${result.resultCode}")
        }
    }

    Button(
        onClick = {
            if (activity == null) {
                onPaymentFailed("Context is not an Activity")
                return@Button
            }
            try {
                val paymentDataRequestJson = """
                    {
                      "apiVersion": 2,
                      "apiVersionMinor": 0,
                      "allowedPaymentMethods": [
                        {
                          "type": "CARD",
                          "parameters": {
                            "allowedAuthMethods": ["PAN_ONLY", "CRYPTOGRAM_3DS"],
                            "allowedCardNetworks": ["AMEX", "DISCOVER", "JCB", "MASTERCARD", "VISA"]
                          },
                          "tokenizationSpecification": {
                            "type": "PAYMENT_GATEWAY",
                            "parameters": {
                              "gateway": "example",
                              "gatewayMerchantId": "exampleGatewayMerchantId"
                            }
                          }
                        }
                      ],
                      "transactionInfo": {
                        "totalPriceStatus": "FINAL",
                        "totalPrice": "$amount",
                        "currencyCode": "$currencyCode"
                      },
                      "merchantInfo": {
                        "merchantName": "TaskFlow Pro / Rewards Gateway"
                      }
                    }
                """.trimIndent()

                val request = PaymentDataRequest.fromJson(paymentDataRequestJson)
                AutoResolveHelper.resolveTask(
                    paymentsClient.loadPaymentData(request),
                    activity,
                    10001
                )
            } catch (e: Exception) {
                onPaymentFailed(e.message ?: "Unknown Google Pay error")
            }
        },
        modifier = modifier
            .height(48.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.Default.Payment,
                contentDescription = "Google Pay",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Pay with Google Pay",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun GoogleWalletAddPassButton(
    passName: String,
    onPassAdded: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    Button(
        onClick = {
            if (activity == null) return@Button
            try {
                onPassAdded()
            } catch (e: Exception) {
                // handle error
            }
        },
        modifier = modifier
            .height(48.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F2937))
    ) {
        Text(
            text = "Add $passName to Google Wallet",
            color = Color(0xFF38BDF8),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
}
