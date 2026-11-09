package com.anis.glive.data

enum class Lang(val label: String) {
    FR("Français"),
    AR("Arabe"),
    EN("Anglais"),
    ES("Espagnol"),
    IT("Italien"),
    DE("Allemand"),
    PT("Portugais"),
    TR("Turc"),
    NL("Néerlandais"),
    CA("Canada"),
    OTHER("Autre");

    companion object {
        private val tokens: Map<String, Lang> = buildMap {
            listOf("FR", "FRA", "FRANCE", "FRENCH", "FRANCAIS", "VF", "VFF", "VOSTFR").forEach { put(it, FR) }
            listOf("AR", "ARA", "ARAB", "ARABIC", "ARABE", "DZ", "ALG", "ALGERIE", "ALGERIA", "MA", "MAROC", "MOROCCO",
                "TN", "TUNISIE", "TUNISIA", "MENA", "EG", "EGYPT", "KSA", "UAE", "QATAR", "OSN").forEach { put(it, AR) }
            listOf("UK", "EN", "ENG", "GB", "ENGLISH", "USA", "US", "IE", "AU").forEach { put(it, EN) }
            listOf("ES", "ESP", "SPAIN", "ESPANA", "SPANISH", "LATINO", "MX").forEach { put(it, ES) }
            listOf("IT", "ITA", "ITALIA", "ITALY").forEach { put(it, IT) }
            listOf("DE", "GER", "GERMANY", "DEUTSCH", "AT", "CH").forEach { put(it, DE) }
            listOf("PT", "POR", "PORTUGAL", "BR", "BRASIL", "BRAZIL").forEach { put(it, PT) }
            listOf("TR", "TUR", "TURK", "TURKEY", "TURKIYE").forEach { put(it, TR) }
            listOf("NL", "NED", "NETHERLANDS", "BE", "BELGIUM").forEach { put(it, NL) }
            listOf("CA", "CAN", "CANADA", "QC", "QUEBEC").forEach { put(it, CA) }
        }

        fun detect(vararg names: String): Lang {
            for (name in names) {
                val parts = name.uppercase()
                    .replace('É', 'E').replace('È', 'E').replace('Ç', 'C')
                    .split(Regex("[^A-Z]+"))
                    .filter { it.isNotEmpty() }
                if (parts.isEmpty()) continue
                tokens[parts.first()]?.let { return it }
                for (p in parts.take(4)) tokens[p]?.let { if (p.length >= 3 || p == parts.first()) return it }
            }
            return OTHER
        }
    }
}
