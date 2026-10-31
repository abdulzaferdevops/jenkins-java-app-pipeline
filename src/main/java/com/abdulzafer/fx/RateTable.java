package com.abdulzafer.fx;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Reference rates expressed as units of each currency per one unit of the base currency.
 * All arithmetic uses BigDecimal with banker's rounding (HALF_EVEN).
 */
public final class RateTable {

    static final int RESULT_SCALE = 4;
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000000");

    private final String base;
    private final Map<String, BigDecimal> perBase;

    RateTable(String base, Map<String, BigDecimal> perBase) {
        this.base = base;
        this.perBase = new TreeMap<>(perBase);
        if (!this.perBase.containsKey(base) || this.perBase.get(base).compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalStateException("base currency " + base + " must have rate 1");
        }
    }

    /** Loads "base=USD" and "rate.XXX=n" entries from a properties stream. */
    public static RateTable load(InputStream in) throws IOException {
        if (in == null) {
            throw new IOException("rates resource not found");
        }
        Properties props = new Properties();
        props.load(in);
        String base = props.getProperty("base", "USD").trim().toUpperCase(Locale.ROOT);
        Map<String, BigDecimal> rates = new TreeMap<>();
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("rate.")) {
                BigDecimal rate = new BigDecimal(props.getProperty(key).trim());
                if (rate.signum() <= 0) {
                    throw new IllegalStateException("rate for " + key + " must be positive");
                }
                rates.put(key.substring(5).toUpperCase(Locale.ROOT), rate);
            }
        }
        return new RateTable(base, rates);
    }

    public String base() {
        return base;
    }

    public Map<String, BigDecimal> rates() {
        return Map.copyOf(perBase);
    }

    /** Converts an amount between two supported currencies, rounded to 4 decimal places. */
    public BigDecimal convert(String from, String to, BigDecimal amount) {
        BigDecimal fromRate = rateFor(from);
        BigDecimal toRate = rateFor(to);
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be zero or positive");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("amount is too large");
        }
        return amount
                .divide(fromRate, 12, RoundingMode.HALF_EVEN)
                .multiply(toRate)
                .setScale(RESULT_SCALE, RoundingMode.HALF_EVEN);
    }

    private BigDecimal rateFor(String code) {
        if (code == null || !code.matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException("currency must be a 3-letter code");
        }
        BigDecimal rate = perBase.get(code.toUpperCase(Locale.ROOT));
        if (rate == null) {
            throw new IllegalArgumentException("unsupported currency: " + code.toUpperCase(Locale.ROOT));
        }
        return rate;
    }

    String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"base\":").append(Json.quote(base))
          .append(",\"note\":").append(Json.quote("demo reference rates, not live market data"))
          .append(",\"rates\":{");
        boolean first = true;
        for (Map.Entry<String, BigDecimal> e : perBase.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            sb.append(Json.quote(e.getKey())).append(':').append(e.getValue().toPlainString());
            first = false;
        }
        return sb.append("}}").toString();
    }
}
