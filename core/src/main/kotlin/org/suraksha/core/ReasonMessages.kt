package org.suraksha.core

object ReasonMessages {
    private val messages = mapOf(
        ReasonCode.FILE_SCAN to listOf("This file was checked locally.", "इस फ़ाइल की जाँच फ़ोन पर हुई।", "ਇਸ ਫ਼ਾਈਲ ਦੀ ਜਾਂਚ ਫ਼ੋਨ ਉੱਤੇ ਹੋਈ।"),
        ReasonCode.EICAR_TEST to listOf("This is the harmless EICAR test file. It is not a real virus.", "यह हानिरहित EICAR परीक्षण फ़ाइल है। यह असली वायरस नहीं है।", "ਇਹ ਨੁਕਸਾਨ ਰਹਿਤ EICAR ਟੈਸਟ ਫ਼ਾਈਲ ਹੈ। ਇਹ ਅਸਲੀ ਵਾਇਰਸ ਨਹੀਂ ਹੈ।"),
        ReasonCode.DEMO_ONLY to listOf("Test alert. This is not a real risk assessment.", "परीक्षण चेतावनी है। यह असली ख़तरे का आकलन नहीं है।", "ਇਹ ਟੈਸਟ ਚੇਤਾਵਨੀ ਹੈ। ਇਹ ਅਸਲੀ ਖ਼ਤਰੇ ਦੀ ਜਾਂਚ ਨਹੀਂ ਹੈ।"),
        ReasonCode.TRUSTED_DOMAIN to listOf("This is an exact match for a trusted domain.", "यह भरोसेमंद डोमेन से ठीक मेल खाता है।", "ਇਹ ਭਰੋਸੇਯੋਗ ਡੋਮੇਨ ਨਾਲ ਪੂਰੀ ਤਰ੍ਹਾਂ ਮੇਲ ਖਾਂਦਾ ਹੈ।"),
        ReasonCode.KNOWN_PHISHING to listOf("This domain is in the offline phishing list.", "यह डोमेन ऑफ़लाइन धोखाधड़ी सूची में है।", "ਇਹ ਡੋਮੇਨ ਆਫ਼ਲਾਈਨ ਧੋਖਾਧੜੀ ਸੂਚੀ ਵਿੱਚ ਹੈ।"),
        ReasonCode.PUNYCODE to listOf("The address uses encoded international characters. Check it carefully.", "पते में बदले हुए अंतरराष्ट्रीय अक्षर हैं। ध्यान से जाँचें।", "ਪਤੇ ਵਿੱਚ ਬਦਲੇ ਹੋਏ ਅੰਤਰਰਾਸ਼ਟਰੀ ਅੱਖਰ ਹਨ। ਧਿਆਨ ਨਾਲ ਜਾਂਚੋ।"),
        ReasonCode.LOOKALIKE to listOf("The address looks like a known brand with changed letters.", "पता किसी मशहूर नाम जैसा है, लेकिन अक्षर बदले हुए हैं।", "ਪਤਾ ਕਿਸੇ ਜਾਣੇ-ਪਛਾਣੇ ਨਾਮ ਵਰਗਾ ਹੈ, ਪਰ ਅੱਖਰ ਬਦਲੇ ਹੋਏ ਹਨ।"),
        ReasonCode.BRAND_IN_SUBDOMAIN to listOf("A familiar brand name hides a different website owner.", "जान-पहचान के नाम के पीछे किसी और की वेबसाइट है।", "ਜਾਣੇ-ਪਛਾਣੇ ਨਾਮ ਦੇ ਪਿੱਛੇ ਕਿਸੇ ਹੋਰ ਦੀ ਵੈੱਬਸਾਈਟ ਹੈ।"),
        ReasonCode.IP_HOST to listOf("This link uses a number instead of a website name.", "इस लिंक में वेबसाइट के नाम की जगह नंबर है।", "ਇਸ ਲਿੰਕ ਵਿੱਚ ਵੈੱਬਸਾਈਟ ਦੇ ਨਾਮ ਦੀ ਥਾਂ ਨੰਬਰ ਹੈ।"),
        ReasonCode.RISKY_TLD to listOf("The address ending is often used in suspicious links.", "पते का यह आख़िरी हिस्सा संदिग्ध लिंक में अक्सर दिखता है।", "ਪਤੇ ਦਾ ਇਹ ਆਖ਼ਰੀ ਹਿੱਸਾ ਸ਼ੱਕੀ ਲਿੰਕਾਂ ਵਿੱਚ ਅਕਸਰ ਦਿਖਦਾ ਹੈ।"),
        ReasonCode.MANY_HYPHENS to listOf("The website name has an unusual number of dashes.", "वेबसाइट के नाम में असामान्य रूप से कई डैश हैं।", "ਵੈੱਬਸਾਈਟ ਦੇ ਨਾਮ ਵਿੱਚ ਅਸਾਧਾਰਨ ਤੌਰ ਉੱਤੇ ਕਈ ਡੈਸ਼ ਹਨ।"),
        ReasonCode.LONG_URL to listOf("This address is unusually long.", "यह पता असामान्य रूप से लंबा है।", "ਇਹ ਪਤਾ ਅਸਾਧਾਰਨ ਤੌਰ ਉੱਤੇ ਲੰਮਾ ਹੈ।"),
        ReasonCode.SHORTENER to listOf("This short link hides its destination. It cannot be checked offline.", "यह छोटा लिंक असली पता छिपाता है। उसे ऑफ़लाइन नहीं खोला जा सकता।", "ਇਹ ਛੋਟਾ ਲਿੰਕ ਅਸਲੀ ਪਤਾ ਲੁਕਾਉਂਦਾ ਹੈ। ਉਹ ਆਫ਼ਲਾਈਨ ਨਹੀਂ ਵੇਖਿਆ ਜਾ ਸਕਦਾ।"),
        ReasonCode.CREDENTIAL_PATH to listOf("The link refers to sign-in, verification or payment details.", "लिंक में लॉगिन, पहचान की जाँच या भुगतान की बात है।", "ਲਿੰਕ ਵਿੱਚ ਲੌਗਇਨ, ਪਛਾਣ ਦੀ ਜਾਂਚ ਜਾਂ ਭੁਗਤਾਨ ਦੀ ਗੱਲ ਹੈ।"),
        ReasonCode.USER_INFO to listOf("The address contains an @ sign that can disguise the real website.", "पते में @ चिह्न है जो असली वेबसाइट छिपा सकता है।", "ਪਤੇ ਵਿੱਚ @ ਚਿੰਨ੍ਹ ਹੈ ਜੋ ਅਸਲੀ ਵੈੱਬਸਾਈਟ ਲੁਕਾ ਸਕਦਾ ਹੈ।"),
        ReasonCode.URL_MODEL to listOf("This link shows warning signs seen in harmful links.", "इस लिंक में हानिकारक लिंक जैसे संकेत मिले हैं।", "ਇਸ ਲਿੰਕ ਵਿੱਚ ਨੁਕਸਾਨਦੇਹ ਲਿੰਕਾਂ ਵਰਗੇ ਸੰਕੇਤ ਹਨ।"),
        ReasonCode.SCAM_LANGUAGE to listOf("The message uses language associated with scams or pressure.", "संदेश में धोखाधड़ी या दबाव डालने जैसी भाषा है।", "ਸੁਨੇਹੇ ਵਿੱਚ ਧੋਖਾਧੜੀ ਜਾਂ ਦਬਾਅ ਪਾਉਣ ਵਰਗੀ ਭਾਸ਼ਾ ਹੈ।"),
        ReasonCode.INVALID_URL to listOf("The website address could not be read safely.", "वेबसाइट का पता सुरक्षित तरीके से पढ़ा नहीं जा सका।", "ਵੈੱਬਸਾਈਟ ਦਾ ਪਤਾ ਸੁਰੱਖਿਅਤ ਢੰਗ ਨਾਲ ਪੜ੍ਹਿਆ ਨਹੀਂ ਜਾ ਸਕਿਆ।"),
        ReasonCode.KNOWN_BAD_HASH to listOf("This file matches a known harmful-file signature.", "यह फ़ाइल ज्ञात नुकसानदेह फ़ाइल से मेल खाती है।", "ਇਹ ਫ਼ਾਈਲ ਜਾਣੀ-ਪਛਾਣੀ ਨੁਕਸਾਨਦੇਹ ਫ਼ਾਈਲ ਨਾਲ ਮੇਲ ਖਾਂਦੀ ਹੈ।"),
        ReasonCode.RISKY_PERMISSIONS to listOf("This app asks for powerful phone access. Only install it if you trust its source.", "यह ऐप फ़ोन पर शक्तिशाली अनुमतियाँ माँगता है। भरोसेमंद जगह से ही इसे इंस्टॉल करें।", "ਇਹ ਐਪ ਫ਼ੋਨ ਉੱਤੇ ਸ਼ਕਤੀਸ਼ਾਲੀ ਇਜਾਜ਼ਤਾਂ ਮੰਗਦਾ ਹੈ। ਭਰੋਸੇਯੋਗ ਥਾਂ ਤੋਂ ਹੀ ਇਸ ਨੂੰ ਇੰਸਟਾਲ ਕਰੋ।"),
        ReasonCode.SUSPICIOUS_FILENAME to listOf("This APK name resembles a common scam lure.", "इस APK का नाम आम धोखाधड़ी के बहाने जैसा है।", "ਇਸ APK ਦਾ ਨਾਮ ਆਮ ਧੋਖਾਧੜੀ ਦੇ ਬਹਾਨੇ ਵਰਗਾ ਹੈ।"),
        ReasonCode.UNREADABLE_FILE to listOf("This file could not be checked. Treat it with caution.", "इस फ़ाइल की जाँच नहीं हो सकी। सावधानी बरतें।", "ਇਸ ਫ਼ਾਈਲ ਦੀ ਜਾਂਚ ਨਹੀਂ ਹੋ ਸਕੀ। ਸਾਵਧਾਨ ਰਹੋ।"),
        ReasonCode.NO_KNOWN_THREAT to listOf("No known warning signs were found. This is not a guarantee.", "कोई ज्ञात ख़तरे का संकेत नहीं मिला। यह गारंटी नहीं है।", "ਕੋਈ ਜਾਣਿਆ ਖ਼ਤਰੇ ਦਾ ਸੰਕੇਤ ਨਹੀਂ ਮਿਲਿਆ। ਇਹ ਗਾਰੰਟੀ ਨਹੀਂ ਹੈ।")
    )
    fun get(code: ReasonCode, language: String): String = messages.getValue(code)[when (language.substringBefore('-')) { "hi" -> 1; "pa" -> 2; else -> 0 }]
}
