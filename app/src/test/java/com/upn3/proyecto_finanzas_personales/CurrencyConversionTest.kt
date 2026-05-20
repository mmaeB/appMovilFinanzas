package com.upn3.proyecto_finanzas_personales

import com.upn3.proyecto_finanzas_personales.model.Wallet
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyConversionTest {

    @Test
    fun testCurrencyConversionLogic() {
        val fromWallet = Wallet(name = "Wallet USD", currencyCode = "USD", balance = 100.0)
        val toWallet = Wallet(name = "Wallet PEN", currencyCode = "PEN", balance = 0.0)
        
        val amountToTransfer = 50.0
        val exchangeRate = 3.75 // 1 USD = 3.75 PEN
        
        val convertedAmount = amountToTransfer * exchangeRate
        
        val newFromBalance = fromWallet.balance - amountToTransfer
        val newToBalance = toWallet.balance + convertedAmount
        
        assertEquals(50.0, newFromBalance, 0.001)
        assertEquals(187.5, newToBalance, 0.001)
    }

    @Test
    fun testBalanceAdjustmentOnCurrencyChange() {
        val wallet = Wallet(name = "My Wallet", currencyCode = "USD", balance = 100.0)
        val newCurrencyCode = "EUR"
        val usdToEurRate = 0.92 // 1 USD = 0.92 EUR
        
        val newBalance = wallet.balance * usdToEurRate
        val updatedWallet = wallet.copy(currencyCode = newCurrencyCode, balance = newBalance)
        
        assertEquals("EUR", updatedWallet.currencyCode)
        assertEquals(92.0, updatedWallet.balance, 0.001)
    }
}
