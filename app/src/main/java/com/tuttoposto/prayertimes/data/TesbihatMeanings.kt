package com.tuttoposto.prayertimes.data

import java.text.Normalizer

/**
 * Namaz tesbihatındaki her grubun (dua/zikir/ayet) Türkçe anlamı.
 *
 * herkul.app/tesbihat içeriğinde satır-satır meal yer almadığı için anlamlar buraya
 * ayrıca yazılmıştır (Kuran ayetleri için Diyanet meali esas alınmıştır). Anlam, grubun
 * içeriğine/açıklamasına göre [meaningForGroup] ile eşleştirilir; böylece hem Okunuş hem
 * Arapça modunda aynı anlam gösterilir.
 *
 * NOT: Anlam metinlerini buradan gözden geçirip düzeltebilirsiniz.
 */

private fun normalizeForMatch(s: String): String {
    val stripped = Normalizer.normalize(s, Normalizer.Form.NFKD)
        .replace(Regex("\\p{Mn}+"), "")   // combining marks (Latin diacritics + Arabic harakat)
        .replace("ـ", "")            // Arabic tatweel
        .lowercase()
    val sb = StringBuilder(stripped.length)
    for (c in stripped) {
        sb.append(if (c in 'a'..'z' || c == ' ' || c in '؀'..'ۿ') c else ' ')
    }
    return sb.toString().replace(Regex("\\s+"), " ").trim()
}

/**
 * Ordered matchers. The first matcher whose any trigger (normalized) appears in the
 * normalized "instruction + recited text" haystack wins. Order matters: more specific first.
 */
private val MATCHERS: List<Pair<List<String>, String>> = listOf(
    listOf("Haşr sûresi") to
        "Şüphesiz biz emanetimizi… O, kendisinden başka hiçbir ilah olmayan Allah'tır; gaybı da " +
        "görüneni de bilendir; O Rahmân'dır, Rahîm'dir. O, kendisinden başka ilah olmayan; mülkün " +
        "sahibi, mukaddes, esenlik veren, güven veren, gözetip koruyan, mutlak güç sahibi, " +
        "iradesini her şeye geçiren, büyüklükte eşi olmayan Allah'tır. Allah, onların ortak " +
        "koştuklarından münezzehtir. O, yaratan, yoktan var eden, şekil verendir. En güzel isimler " +
        "O'nundur. Göklerdeki ve yerdeki her şey O'nu tesbih eder. O, mutlak güç ve hikmet " +
        "sahibidir. (Haşr, 59/22-24)",

    listOf("Fetih sûresi") to
        "Muhammed Allah'ın elçisidir. Onunla beraber olanlar inkârcılara karşı çetin, birbirlerine " +
        "karşı pek merhametlidirler. Onları rükûya varırken, secde ederken görürsün; Allah'tan " +
        "lütuf ve hoşnutluk isterler. Yüzlerinde secde izinden nişanları vardır… (Fetih, 48/29)",

    listOf("Nebe’ Sûresi", "Nebe Sûresi") to
        "Şüphesiz takvâ sahipleri için kurtuluş, bahçeler, üzüm bağları, yaşıt güzeller ve dolu " +
        "kadehler vardır. Orada ne boş bir söz ne de yalan işitirler. Bunlar Rabbinden yeterli bir " +
        "bağıştır. O, göklerin, yerin ve ikisi arasındakilerin Rabbidir; Rahmân'dır. O gün Ruh " +
        "(Cebrail) ve melekler saf saf dururlar; Rahmân'ın izin verdiğinden başkası konuşamaz ve " +
        "konuşan da doğruyu söyler. İşte bu, hak olan gündür… O gün kişi elleriyle gönderdiğine " +
        "bakar; inkârcı ise 'Keşke toprak olsaydım!' der. (Nebe, 78/31-40)",

    listOf("Bakara sûresi 285", "Âmenerras") to
        "Peygamber, Rabbinden kendisine indirilene iman etti, müminler de. Hepsi Allah'a, " +
        "meleklerine, kitaplarına ve peygamberlerine iman etti. 'O'nun peygamberlerinden hiçbirini " +
        "ayırmayız' dediler ve 'İşittik, itaat ettik; bağışlamanı dileriz Rabbimiz, dönüş sanadır' " +
        "dediler. Allah kimseye gücünün yeteceğinden fazlasını yüklemez. Herkesin kazandığı iyilik " +
        "lehine, kötülük aleyhinedir. 'Rabbimiz! Unutur veya yanılırsak bizi sorumlu tutma. " +
        "Bize gücümüzün yetmeyeceği yükü yükleme. Bizi affet, bağışla, bize merhamet et. Sen " +
        "Mevlâmızsın; inkârcılara karşı bize yardım et.' (Bakara, 2/285-286)",

    listOf("İsm-i A’zam", "İsm-i A'zam", "Tercümân") to
        "İsm-i A'zam (Allah'ın en yüce ismi) hürmetine yapılan uzun bir niyazdır. Allah'ın güzel " +
        "isimleri tek tek anılarak, her birinin hürmetine bizi cehennem ateşinden koruması, " +
        "affetmesi ve rahmetiyle muamele etmesi istenir.",

    // Salavat (Ahzâb 56) — Arapça nüshada Tercüman öncesi ayrı bölüm
    listOf("innallâhe ve melâiketehû", "melâiketehû yusallûne", "aşağıdaki duâlarla devam", "ان الله وملائكته يصلون") to
        "Şüphesiz Allah ve melekleri Peygamber'e salât eder. Ey iman edenler! Siz de ona salât " +
        "edin ve tam bir teslimiyetle selâm verin. (Ahzâb, 33/56) Efendimiz Muhammed'e ve âline " +
        "salât ü selâm olsun.",

    listOf("Âyetü'l Kürsî", "Âyetü’l Kürsî") to
        "Allah, kendisinden başka hiçbir ilah olmayandır. O, Hayy'dır (diridir), Kayyûm'dur (her " +
        "şeyi ayakta tutandır). O'nu ne uyuklama tutar ne de uyku. Göklerde ve yerde ne varsa " +
        "O'nundur. İzni olmadan O'nun katında kim şefaat edebilir? O, kullarının önlerindekini de " +
        "arkalarındakini de bilir; onlar O'nun ilminden ancak dilediği kadarını kavrarlar. O'nun " +
        "kürsüsü gökleri ve yeri kaplamıştır; bunları koruyup gözetmek O'na ağır gelmez. O, " +
        "yücedir, büyüktür. (Bakara, 2/255)",

    listOf("Salâten Tüncînâ", "Tüncînâ", "تنجينا") to
        "Allah'ım! Efendimiz Muhammed'e ve âline öyle bir rahmet eyle ki, onunla bizi bütün korku " +
        "ve âfetlerden kurtar, bütün ihtiyaçlarımızı gider, bizi bütün günahlardan temizle, " +
        "katında derecelerin en yükseğine çıkar ve hayatta da öldükten sonra da hayırların en " +
        "yüce gayesine ulaştır. Duaları kabul eden Allah'ım, âmin. Hamd âlemlerin Rabbi Allah'a " +
        "mahsustur.",

    listOf("ecirnâ mine", "ecirnâ min", "اجرنا من") to
        "Allah'ım! Bizi cehennem ateşinden ve her türlü ateşten koru. Bizi dinî ve dünyevî " +
        "fitnelerden, âhir zaman fitnesinden, Deccal ve Süfyan fitnesinden; dalâletlerden, " +
        "bid'atlerden ve belâlardan; kötülüğü emreden nefsin şerrinden; kabir azabından ve " +
        "kıyamet gününün azabından koru.",

    listOf("afvike Yâ Mucîr", "بعفوك يا مجير") to
        "Affınla ey sığınılan (Mucîr), lütfunla ey çok bağışlayan (Gaffâr), rahmetinle ey " +
        "merhametlilerin en merhametlisi (Erhamerrâhimîn)! Bizi cehennem ateşinden kurtar ve bizi " +
        "iyilerle birlikte cennetine koy.",

    listOf("nukaddimu", "نقدم") to
        "Allah'ım! Göklerin ve yerin ehlinin her nefeste, her an ve göz açıp kapayıncaya kadar " +
        "geçen her lahzada getireceği şehadetler sayısınca, senin huzurunda şehadet getiririz: " +
        "Şehadet ederiz ki Allah'tan başka ilah yoktur.",

    listOf("bukraten ve esîlâ", "bukraten ve esîla", "بكرة واصيلا") to
        "Sübhânallâh (33 defa): Allah'ı her türlü eksiklikten tenzih ederim. Elhamdülillâh (33 " +
        "defa): Hamd Allah'a mahsustur. Allâhü ekber (33 defa): Allah en büyüktür.",

    listOf("cümle-i tevhid", "vahdehû lâ şerîke", "وحده لا شريك") to
        "Allah'tan başka ilah yoktur; O birdir, ortağı yoktur. Mülk O'nundur, hamd O'na mahsustur. " +
        "O diriltir ve öldürür; kendisi ölümsüz, daima diridir. Bütün hayır O'nun elindedir; O her " +
        "şeye kâdirdir ve dönüş ancak O'nadır.",

    listOf("Estağfirullâh", "استغفر الله") to
        "Kendisinden başka ilah olmayan; Hayy ve Kayyûm olan yüce Allah'tan bağışlanma diler, O'na " +
        "tövbe ederim (3 defa). Allah'ım! Selâm sensin, selâmet ancak sendendir. Ey celâl ve ikram " +
        "sahibi, sen yücesin, mübareksin.",

    listOf("ve’l-hamdu lillâhi ve lâ ilâhe", "velhamdu lillahi ve la ilahe", "والحمد لله ولا اله") to
        "Allah'ı tenzih ederim; hamd Allah'a mahsustur; Allah'tan başka ilah yoktur; Allah en " +
        "büyüktür. Güç ve kuvvet ancak yüce ve azîm olan Allah'tandır.",

    listOf("namaz duâsı yapılır", "fa’lem ennehû", "fe’lem ennehû", "felem ennehu", "فاعلم انه") to
        "Bil ki Allah'tan başka ilah yoktur (Muhammed, 47/19). Ardından 'Lâ ilâhe illallah' 33 " +
        "defa söylenir; sonuncusunda 'Muhammedün Resûlullah' eklenir. Sonra kişi kendi namaz " +
        "duasını yapar.",

    listOf("âhiyyen şerâhiyyen", "اهيا شراهيا", "شراهيا") to
        "Âmin. Seni tenzih ederim ey (İsm-i A'zam'la anılan) Rabbim; sen pek yücesin, senden başka " +
        "ilah yoktur. Bizi; üstadımızı, ana-babamızı, akraba ve dostlarımızı, kardeşlerimizi ve " +
        "bütün mümin kardeşlerimizi cehennem ateşinden koru.",

    listOf("min kulli nâr, vehfeznâ", "vehfeznâ min şerri", "واحفظنا من شر") to
        "…ve her türlü ateşten (bizi koru). Bizi nefsin ve şeytanın şerrinden, cin ve insanın " +
        "şerrinden; bid'at, dalâlet, inkâr ve azgınlığın şerrinden koru.",

    listOf("Yâ Rabbe’s-semâvâti", "يا رب السماوات") to
        "Ey göklerin ve yerin Rabbi! Ey celâl ve ikram sahibi! Bütün bu güzel isimlerin hürmetine " +
        "senden, Efendimiz Muhammed'e ve âline salât etmeni dileriz. İbrahim'e ve âline rahmet " +
        "ettiğin gibi Efendimiz Muhammed'e de rahmet eyle. Rabbimiz, şüphesiz sen övgüye lâyıksın, " +
        "şanı yücesin. Rahmetinle, ey merhametlilerin en merhametlisi! Hamd âlemlerin Rabbi Allah'a " +
        "mahsustur."
)

// Precompute normalized triggers once.
private val NORM_MATCHERS: List<Pair<List<String>, String>> =
    MATCHERS.map { (trigs, meaning) -> trigs.map { normalizeForMatch(it) } to meaning }

/**
 * Returns the Turkish meaning for a tesbihat group, matched from its narration ([instruction])
 * and recited text ([recitedText]); null if no meaning is associated.
 */
fun meaningForGroup(instruction: String?, recitedText: String): String? {
    val hay = normalizeForMatch((instruction ?: "") + " || " + recitedText)
    for ((trigs, meaning) in NORM_MATCHERS) {
        if (trigs.any { it.isNotEmpty() && hay.contains(it) }) return meaning
    }
    return null
}
