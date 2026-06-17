package com.tuttoposto.prayertimes.data

/**
 * Esmaül Hüsna (99 İsim) ve beş vakit tanımı.
 *
 * Namaz tesbihatının vakte özel tam metni (okunuş + Arapça) [TesbihatContent.kt] içindedir
 * ve kaynağı herkul.app/tesbihat'tır. Burada yalnızca [Vakit] tanımı ve [ESMAUL_HUSNA] listesi yer alır.
 *
 * NOT: Esmaül Hüsna metinlerini buradan gözden geçirip düzeltebilirsiniz.
 */

/** Beş vakit. [prayerName] uygulamadaki [com.tuttoposto.prayertimes.data.models.Prayer.displayName] ile eşleşir. */
enum class Vakit(val displayTr: String, val prayerName: String) {
    SABAH("Sabah", "Fajr"),
    OGLE("Öğle", "Dhuhr"),
    IKINDI("İkindi", "Asr"),
    AKSAM("Akşam", "Maghrib"),
    YATSI("Yatsı", "Isha");

    companion object {
        /** "Fajr", "Dhuhr"… prayer adından Vakit'i bulur (büyük/küçük harf duyarsız). */
        fun fromPrayerName(name: String?): Vakit? =
            entries.find { it.prayerName.equals(name, ignoreCase = true) }
    }
}

/** Esmaül Hüsna'dan tek bir isim. */
data class DivineName(
    val order: Int,
    val arabic: String,
    val transliteration: String,
    val meaning: String
)

// ---------------------------------------------------------------------------
// Esmaül Hüsna (99 İsim)
// ---------------------------------------------------------------------------

val ESMAUL_HUSNA: List<DivineName> = listOf(
    DivineName(1, "اَلرَّحْمٰنُ", "Er-Rahmân", "Dünyada bütün mahlûkata merhamet eden"),
    DivineName(2, "اَلرَّحٖيمُ", "Er-Rahîm", "Ahirette müminlere sonsuz merhamet eden"),
    DivineName(3, "اَلْمَلِكُ", "El-Melik", "Mülkün, bütün kâinatın gerçek sahibi"),
    DivineName(4, "اَلْقُدُّوسُ", "El-Kuddûs", "Her türlü eksiklikten münezzeh olan"),
    DivineName(5, "اَلسَّلَامُ", "Es-Selâm", "Esenlik veren, kullarını selâmete çıkaran"),
    DivineName(6, "اَلْمُؤْمِنُ", "El-Mü'min", "Güven veren, vaadine güvenilen"),
    DivineName(7, "اَلْمُهَيْمِنُ", "El-Müheymin", "Her şeyi görüp gözeten ve koruyan"),
    DivineName(8, "اَلْعَزٖيزُ", "El-Azîz", "Mağlup edilemeyen, izzet ve şeref sahibi"),
    DivineName(9, "اَلْجَبَّارُ", "El-Cebbâr", "Dilediğini zorla yaptırmaya gücü yeten"),
    DivineName(10, "اَلْمُتَكَبِّرُ", "El-Mütekebbir", "Büyüklük ve azamette eşi olmayan"),
    DivineName(11, "اَلْخَالِقُ", "El-Hâlık", "Her şeyi yoktan yaratan"),
    DivineName(12, "اَلْبَارِئُ", "El-Bârî", "Her şeyi kusursuz ve uyumlu yaratan"),
    DivineName(13, "اَلْمُصَوِّرُ", "El-Musavvir", "Varlıklara şekil ve suret veren"),
    DivineName(14, "اَلْغَفَّارُ", "El-Gaffâr", "Günahları çokça bağışlayan"),
    DivineName(15, "اَلْقَهَّارُ", "El-Kahhâr", "Her şeye galip gelen, kahreden"),
    DivineName(16, "اَلْوَهَّابُ", "El-Vehhâb", "Karşılıksız çokça veren, bağışlayan"),
    DivineName(17, "اَلرَّزَّاقُ", "Er-Razzâk", "Bütün rızıkları yaratan ve veren"),
    DivineName(18, "اَلْفَتَّاحُ", "El-Fettâh", "Hayır kapılarını açan, hüküm veren"),
    DivineName(19, "اَلْعَلٖيمُ", "El-Alîm", "Her şeyi hakkıyla bilen"),
    DivineName(20, "اَلْقَابِضُ", "El-Kâbız", "Dilediğine darlık veren, ruhları kabzeden"),
    DivineName(21, "اَلْبَاسِطُ", "El-Bâsıt", "Dilediğine bolluk ve genişlik veren"),
    DivineName(22, "اَلْخَافِضُ", "El-Hâfıd", "Alçaltan, dereceleri düşüren"),
    DivineName(23, "اَلرَّافِعُ", "Er-Râfi'", "Yükselten, dereceleri yükselten"),
    DivineName(24, "اَلْمُعِزُّ", "El-Mu'iz", "İzzet ve şeref veren, yücelten"),
    DivineName(25, "اَلْمُذِلُّ", "El-Müzill", "Zillete düşüren, alçaltan"),
    DivineName(26, "اَلسَّمٖيعُ", "Es-Semî'", "Her şeyi işiten"),
    DivineName(27, "اَلْبَصٖيرُ", "El-Basîr", "Her şeyi gören"),
    DivineName(28, "اَلْحَكَمُ", "El-Hakem", "Mutlak hüküm veren, hâkim"),
    DivineName(29, "اَلْعَدْلُ", "El-Adl", "Mutlak adaletli olan"),
    DivineName(30, "اَللَّطٖيفُ", "El-Latîf", "Lütufkâr, en ince işleri bilen"),
    DivineName(31, "اَلْخَبٖيرُ", "El-Habîr", "Her şeyin iç yüzünden haberdar olan"),
    DivineName(32, "اَلْحَلٖيمُ", "El-Halîm", "Acele etmeyen, yumuşak davranan"),
    DivineName(33, "اَلْعَظٖيمُ", "El-Azîm", "Pek azametli, ulu"),
    DivineName(34, "اَلْغَفُورُ", "El-Gafûr", "Çok bağışlayan"),
    DivineName(35, "اَلشَّكُورُ", "Eş-Şekûr", "Az amele çok mükâfat veren"),
    DivineName(36, "اَلْعَلِىُّ", "El-Aliyy", "Pek yüce olan"),
    DivineName(37, "اَلْكَبٖيرُ", "El-Kebîr", "Pek büyük olan"),
    DivineName(38, "اَلْحَفٖيظُ", "El-Hafîz", "Her şeyi koruyup gözeten"),
    DivineName(39, "اَلْمُقٖيتُ", "El-Mukît", "Her canlının rızkını veren, gücü yeten"),
    DivineName(40, "اَلْحَسٖيبُ", "El-Hasîb", "Hesaba çeken, kullarına kâfi gelen"),
    DivineName(41, "اَلْجَلٖيلُ", "El-Celîl", "Celâl ve azamet sahibi"),
    DivineName(42, "اَلْكَرٖيمُ", "El-Kerîm", "Çok cömert, ikram sahibi"),
    DivineName(43, "اَلرَّقٖيبُ", "Er-Rakîb", "Her şeyi gözetleyen, kontrol eden"),
    DivineName(44, "اَلْمُجٖيبُ", "El-Mucîb", "Dualara cevap veren"),
    DivineName(45, "اَلْوَاسِعُ", "El-Vâsi'", "İlmi ve rahmeti her şeyi kuşatan"),
    DivineName(46, "اَلْحَكٖيمُ", "El-Hakîm", "Her işi hikmetli olan"),
    DivineName(47, "اَلْوَدُودُ", "El-Vedûd", "Kullarını çok seven, sevilmeye layık olan"),
    DivineName(48, "اَلْمَجٖيدُ", "El-Mecîd", "Şanı yüce ve şerefli olan"),
    DivineName(49, "اَلْبَاعِثُ", "El-Bâis", "Ölüleri dirilten"),
    DivineName(50, "اَلشَّهٖيدُ", "Eş-Şehîd", "Her şeye şahit olan"),
    DivineName(51, "اَلْحَقُّ", "El-Hakk", "Varlığı hak olan, gerçek olan"),
    DivineName(52, "اَلْوَكٖيلُ", "El-Vekîl", "Kendisine tevekkül edilen, işleri yöneten"),
    DivineName(53, "اَلْقَوِىُّ", "El-Kaviyy", "Çok kuvvetli olan"),
    DivineName(54, "اَلْمَتٖينُ", "El-Metîn", "Çok sağlam, metanetli olan"),
    DivineName(55, "اَلْوَلِىُّ", "El-Veliyy", "Dost olan, yardım eden"),
    DivineName(56, "اَلْحَمٖيدُ", "El-Hamîd", "Övgüye layık olan"),
    DivineName(57, "اَلْمُحْصٖي", "El-Muhsî", "Her şeyi tek tek sayıp bilen"),
    DivineName(58, "اَلْمُبْدِئُ", "El-Mübdi'", "Yaratmayı baştan başlatan"),
    DivineName(59, "اَلْمُعٖيدُ", "El-Muîd", "Yaratmayı tekrarlayan, ölümden sonra dirilten"),
    DivineName(60, "اَلْمُحْيٖي", "El-Muhyî", "Hayat veren, dirilten"),
    DivineName(61, "اَلْمُمٖيتُ", "El-Mümît", "Ölümü yaratan"),
    DivineName(62, "اَلْحَىُّ", "El-Hayy", "Daima diri olan"),
    DivineName(63, "اَلْقَيُّومُ", "El-Kayyûm", "Her şeyi ayakta tutan, varlığı kendinden"),
    DivineName(64, "اَلْوَاجِدُ", "El-Vâcid", "İstediğini bulan, hiçbir şeye muhtaç olmayan"),
    DivineName(65, "اَلْمَاجِدُ", "El-Mâcid", "Şanı yüce, kerem ve ihsanı bol olan"),
    DivineName(66, "اَلْوَاحِدُ", "El-Vâhid", "Tek olan, ortağı bulunmayan"),
    DivineName(67, "اَلْاَحَدُ", "El-Ehad", "Eşi ve benzeri olmayan, bir olan"),
    DivineName(68, "اَلصَّمَدُ", "Es-Samed", "Hiçbir şeye muhtaç olmayan, her şey kendisine muhtaç olan"),
    DivineName(69, "اَلْقَادِرُ", "El-Kâdir", "Her şeye gücü yeten"),
    DivineName(70, "اَلْمُقْتَدِرُ", "El-Muktedir", "Dilediğini dilediği gibi yapan kudret sahibi"),
    DivineName(71, "اَلْمُقَدِّمُ", "El-Mukaddim", "Dilediğini öne geçiren"),
    DivineName(72, "اَلْمُؤَخِّرُ", "El-Muahhir", "Dilediğini geri bırakan"),
    DivineName(73, "اَلْاَوَّلُ", "El-Evvel", "Başlangıcı olmayan, ilk olan"),
    DivineName(74, "اَلْاٰخِرُ", "El-Âhir", "Sonu olmayan, son olan"),
    DivineName(75, "اَلظَّاهِرُ", "Ez-Zâhir", "Varlığı apaçık olan"),
    DivineName(76, "اَلْبَاطِنُ", "El-Bâtın", "Gizli olan, akılların idrak edemeyeceği"),
    DivineName(77, "اَلْوَالٖي", "El-Vâlî", "Kâinatı tek başına idare eden"),
    DivineName(78, "اَلْمُتَعَالٖي", "El-Müteâlî", "Pek yüce, her türlü noksandan münezzeh"),
    DivineName(79, "اَلْبَرُّ", "El-Berr", "İyilik ve ihsanı bol olan"),
    DivineName(80, "اَلتَّوَّابُ", "Et-Tevvâb", "Tövbeleri çokça kabul eden"),
    DivineName(81, "اَلْمُنْتَقِمُ", "El-Müntekim", "Suçluları adaletle cezalandıran"),
    DivineName(82, "اَلْعَفُوُّ", "El-Afüvv", "Çokça affeden"),
    DivineName(83, "اَلرَّؤُوفُ", "Er-Raûf", "Çok şefkatli, çok esirgeyen"),
    DivineName(84, "مَالِكُ الْمُلْكِ", "Mâlikü'l-Mülk", "Mülkün gerçek ve tek sahibi"),
    DivineName(85, "ذُو الْجَلَالِ وَالْاِكْرَامِ", "Zü'l-Celâli ve'l-İkrâm", "Celâl, azamet ve ikram sahibi"),
    DivineName(86, "اَلْمُقْسِطُ", "El-Muksit", "Adaletle hükmeden"),
    DivineName(87, "اَلْجَامِعُ", "El-Câmi'", "İstediğini istediği zaman toplayan"),
    DivineName(88, "اَلْغَنِىُّ", "El-Ganiyy", "Hiçbir şeye muhtaç olmayan, zengin olan"),
    DivineName(89, "اَلْمُغْنٖي", "El-Mugnî", "Dilediğini zengin kılan, ihtiyaçları gideren"),
    DivineName(90, "اَلْمَانِعُ", "El-Mâni'", "Dilediğine engel olan, koruyan"),
    DivineName(91, "اَلضَّارُّ", "Ed-Dârr", "Hikmeti gereği zarar verenleri yaratan"),
    DivineName(92, "اَلنَّافِعُ", "En-Nâfi'", "Fayda verenleri yaratan"),
    DivineName(93, "اَلنُّورُ", "En-Nûr", "Âlemleri nurlandıran, nur olan"),
    DivineName(94, "اَلْهَادٖي", "El-Hâdî", "Hidayet veren, doğru yola ileten"),
    DivineName(95, "اَلْبَدٖيعُ", "El-Bedî'", "Örneksiz, eşsiz yaratan"),
    DivineName(96, "اَلْبَاقٖي", "El-Bâkî", "Varlığı sonsuz olan, daim olan"),
    DivineName(97, "اَلْوَارِثُ", "El-Vâris", "Her şeyin gerçek sahibi, baki kalan"),
    DivineName(98, "اَلرَّشٖيدُ", "Er-Reşîd", "Doğru yolu gösteren, irşad eden"),
    DivineName(99, "اَلصَّبُورُ", "Es-Sabûr", "Çok sabırlı olan")
)
