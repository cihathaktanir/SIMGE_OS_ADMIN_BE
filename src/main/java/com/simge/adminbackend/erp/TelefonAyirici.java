package com.simge.adminbackend.erp;

/**
 * Telefon numarasını Mikro'nun beklediği üç alana böler (ADR D-173).
 *
 * <p>
 * Mikro telefonu tek parça tutmuyor; ölçüldü, {@code CARI_HESAP_ADRESLERI}
 * içindeki dolu satırların <b>hepsi</b> şu biçimde:
 * </p>
 *
 * <pre>
 *   adr_tel_ulke_kodu  = "90"
 *   adr_tel_bolge_kodu = "312"
 *   adr_tel_no1        = "3976498"   (7 hane)
 * </pre>
 *
 * <h2>Neden bu sınıf var — düzeltilen hata</h2>
 * <p>
 * {@code insert-cari-adres.sql} numaranın <b>tamamını</b> {@code adr_tel_no1}'e
 * yazıyordu ve {@code RegistrationReviewController} o alanı
 * {@code @Size(max = 50)} ile kabul ediyordu. Ama {@code adr_tel_no1}
 * <b>10 karakter</b>: normal bir cep numarası ({@code 05321234567}, 11 hane)
 * INSERT'i truncation hatasıyla düşürürdü. Bugüne kadar patlamamış olmasının
 * tek sebebi girilen numaraların kısa olması.
 * </p>
 *
 * <p>
 * Bölme kuralları <b>tahmin etmiyor</b>: tanınmayan bir biçim geldiğinde
 * numarayı uydurmak yerine son 7 haneyi numara, kalanını (sığıyorsa) alan
 * kodu sayıyor. Hiçbir durumda sütun genişliği aşılmıyor.
 * </p>
 */
public final class TelefonAyirici {

    /** {@code adr_tel_no1} genişliği. */
    private static final int NO_SINIRI = 10;

    /** {@code adr_tel_bolge_kodu} / {@code adr_tel_ulke_kodu} genişliği. */
    private static final int KOD_SINIRI = 4;

    /** Türkiye. Alan kodu ayrılabildiğinde varsayılan ülke kodu. */
    private static final String VARSAYILAN_ULKE = "90";

    /**
     * Bölünmüş telefon. Alanlar hiçbir zaman {@code null} değil — Mikro NULL
     * kabul etmiyor, boş alan {@code ""} olarak yazılıyor.
     */
    public record Telefon(String ulkeKodu, String bolgeKodu, String numara) {

        public static Telefon bos() {
            return new Telefon("", "", "");
        }
    }

    /**
     * Serbest biçimli bir numarayı böler.
     *
     * <p>
     * Tanınan biçimler (rakam dışı her şey atıldıktan sonra):
     * </p>
     * <ul>
     *   <li>{@code 90XXXXXXXXXX} (12 hane) → 90 / XXX / XXXXXXX</li>
     *   <li>{@code 0XXXXXXXXXX} (11 hane) → 90 / XXX / XXXXXXX</li>
     *   <li>{@code XXXXXXXXXX} (10 hane) → 90 / XXX / XXXXXXX</li>
     *   <li>{@code XXXXXXX} (7 hane) → "" / "" / XXXXXXX</li>
     * </ul>
     */
    public static Telefon ayir(String ham) {
        String rakamlar = yalnizcaRakam(ham);
        if (rakamlar.isEmpty()) {
            return Telefon.bos();
        }

        // Uluslararası önek: +90... / 0090...
        if (rakamlar.length() == 12 && rakamlar.startsWith(VARSAYILAN_ULKE)) {
            return bolgeVeNumara(VARSAYILAN_ULKE, rakamlar.substring(2));
        }
        // Şehirlerarası önek: 0312... / 0532...
        if (rakamlar.length() == 11 && rakamlar.startsWith("0")) {
            return bolgeVeNumara(VARSAYILAN_ULKE, rakamlar.substring(1));
        }
        // Öneksiz tam numara: 3123976498
        if (rakamlar.length() == 10) {
            return bolgeVeNumara(VARSAYILAN_ULKE, rakamlar);
        }
        // Yalnızca abone numarası: 3976498
        if (rakamlar.length() <= 7) {
            return new Telefon("", "", rakamlar);
        }

        // Tanınmayan uzunluk. Uydurmak yerine son 7 haneyi numara sayıyoruz;
        // kalanı alan koduna sığıyorsa oraya, sığmıyorsa atılıyor. Sütunu
        // taşırıp INSERT'i düşürmekten iyi.
        String numara = rakamlar.substring(rakamlar.length() - 7);
        String kalan = rakamlar.substring(0, rakamlar.length() - 7);
        String bolge = kalan.length() <= KOD_SINIRI ? kalan : "";
        return new Telefon("", bolge, numara);
    }

    /** 10 haneli yerel numarayı alan kodu + abone numarası olarak böler. */
    private static Telefon bolgeVeNumara(String ulke, String yerel) {
        if (yerel.length() != 10) {
            return new Telefon(ulke, "", kirp(yerel, NO_SINIRI));
        }
        return new Telefon(ulke, yerel.substring(0, 3), yerel.substring(3));
    }

    private static String yalnizcaRakam(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String kirp(String s, int sinir) {
        return s.length() <= sinir ? s : s.substring(0, sinir);
    }

    private TelefonAyirici() {
    }
}
