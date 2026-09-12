package com.balancdapp.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Formatea importes según la divisa preferida del usuario (User.moneda). Es solo una
 * preferencia de visualización: no convierte valores entre divisas, simplemente cambia el
 * símbolo y el formato numérico con el que se muestran (así que 42 sigue siendo 42, ya sea
 * "42,00 €" o "$42.00").
 */
public class MoneyFormatter {

    private final String codigo;
    private final String simbolo;
    private final boolean simboloAlFinal;
    private final DecimalFormat decimalFormat;

    public MoneyFormatter(String codigoMoneda) {
        String c = (codigoMoneda == null || codigoMoneda.isBlank()) ? "EUR" : codigoMoneda.toUpperCase();
        switch (c) {
            case "USD":
                this.codigo = "USD";
                this.simbolo = "$";
                this.simboloAlFinal = false;
                this.decimalFormat = new DecimalFormat("#,##0.00", new DecimalFormatSymbols(Locale.US));
                break;
            case "GBP":
                this.codigo = "GBP";
                this.simbolo = "£";
                this.simboloAlFinal = false;
                this.decimalFormat = new DecimalFormat("#,##0.00", new DecimalFormatSymbols(Locale.UK));
                break;
            case "EUR":
            default:
                this.codigo = "EUR";
                this.simbolo = "€";
                this.simboloAlFinal = true;
                this.decimalFormat = new DecimalFormat("#,##0.00", new DecimalFormatSymbols(Locale.forLanguageTag("es-ES")));
                break;
        }
    }

    /** Usado desde Thymeleaf: ${moneyFormatter.format(importe)} -> "1.234,56 €" / "$1,234.56" / "£1,234.56" */
    public String format(Double importe) {
        double valor = importe == null ? 0.0 : importe;
        String numero = decimalFormat.format(valor);
        return simboloAlFinal ? (numero + " " + simbolo) : (simbolo + numero);
    }

    public String format(double importe) {
        return format(Double.valueOf(importe));
    }

    public String getCodigo() { return codigo; }
    public String getSimbolo() { return simbolo; }
    public boolean isSimboloAlFinal() { return simboloAlFinal; }
}
