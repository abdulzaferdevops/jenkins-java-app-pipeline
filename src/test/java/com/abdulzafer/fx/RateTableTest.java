package com.abdulzafer.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RateTableTest {

    private static RateTable table() throws IOException {
        String props = "base=USD\nrate.USD=1\nrate.OMR=0.3845\nrate.EUR=0.92\n";
        return RateTable.load(new ByteArrayInputStream(props.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void convertsFromBase() throws IOException {
        assertEquals(new BigDecimal("38.4500"), table().convert("USD", "OMR", new BigDecimal("100")));
    }

    @Test
    void convertsToBaseWithBankersRounding() throws IOException {
        // 100 / 0.3845 = 260.07802340... -> 260.0780
        assertEquals(new BigDecimal("260.0780"), table().convert("OMR", "USD", new BigDecimal("100")));
    }

    @Test
    void convertsCrossRateViaBase() throws IOException {
        // 100 OMR -> USD -> EUR = 260.0780234 * 0.92 = 239.2718 (4 dp)
        assertEquals(new BigDecimal("239.2718"), table().convert("OMR", "EUR", new BigDecimal("100")));
    }

    @Test
    void sameCurrencyReturnsAmount() throws IOException {
        assertEquals(new BigDecimal("12.5000"), table().convert("EUR", "EUR", new BigDecimal("12.5")));
    }

    @Test
    void currencyCodesAreCaseInsensitive() throws IOException {
        assertEquals(new BigDecimal("38.4500"), table().convert("usd", "omr", new BigDecimal("100")));
    }

    @Test
    void rejectsUnsupportedCurrency() throws IOException {
        RateTable t = table();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> t.convert("USD", "JPY", BigDecimal.ONE));
        assertEquals("unsupported currency: JPY", e.getMessage());
    }

    @Test
    void rejectsMalformedCurrency() throws IOException {
        RateTable t = table();
        assertThrows(IllegalArgumentException.class, () -> t.convert("US", "OMR", BigDecimal.ONE));
    }

    @Test
    void rejectsNegativeAmount() throws IOException {
        RateTable t = table();
        assertThrows(IllegalArgumentException.class,
                () -> t.convert("USD", "OMR", new BigDecimal("-1")));
    }

    @Test
    void rejectsBaseWithoutUnitRate() {
        String props = "base=USD\nrate.USD=2\n";
        assertThrows(IllegalStateException.class,
                () -> RateTable.load(new ByteArrayInputStream(props.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void bundledRatesAreValid() throws IOException {
        RateTable bundled = App.loadBundledRates();
        assertEquals("USD", bundled.base());
        assertTrue(bundled.rates().containsKey("OMR"), "OMR must be supported");
        assertTrue(bundled.rates().size() >= 5, "expected at least 5 currencies");
    }
}
