package pt.dabs.fueltracker.core.domain.model

/**
 * Marcas de postos em Portugal. Guardadas pelo [code] (estável: nunca o ordinal nem o nome
 * apresentado), para sobreviver a mudanças de nome. Ex.: a Cepsa passou a Moeve; os talões antigos
 * dizem Cepsa, os novos Moeve, e contam como a mesma marca.
 *
 * [brandName] é o nome próprio da marca (null em [OTHER], cujo rótulo vem dos recursos).
 * [aliases] são expressões sobre o texto do talão em maiúsculas e sem acentos. A ordem das
 * entradas resolve empates quando uma linha tem mais do que uma marca.
 */
enum class FuelBrand(val code: String, val brandName: String?, val aliases: Regex?) {
    GALP("GALP", "Galp", Regex("""\bGALP\b|\bPETROGAL\b""")),
    REPSOL("REPSOL", "Repsol", Regex("""\bREPSOL\b""")),
    BP("BP", "BP", Regex("""\bBP\b""")),
    PRIO("PRIO", "Prio", Regex("""\bPRIO\b""")),
    MOEVE("MOEVE", "Moeve (Cepsa)", Regex("""\bMOEVE\b|\bCEPSA\b""")),
    SHELL("SHELL", "Shell", Regex("""\bSHELL\b""")),
    TOTALENERGIES("TOTALENERGIES", "TotalEnergies", Regex("""\bTOTAL ?ENERGIES\b""")),
    INTERMARCHE("INTERMARCHE", "Intermarché", Regex("""\bINTERMARCHE\b|\bITMP\b|\bECOMARCHE\b""")),
    LECLERC("LECLERC", "E.Leclerc", Regex("""\bE\.?\s?LECLERC\b|\bLECLERC\b""")),
    AUCHAN("AUCHAN", "Auchan", Regex("""\bAUCHAN\b|\bJUMBO\b""")),
    CONTINENTE("CONTINENTE", "Continente", Regex("""\bCONTINENTE\b""")),
    PINGO_DOCE("PINGO_DOCE", "Pingo Doce", Regex("""\bPINGO DOCE\b""")),
    ALVES_BANDEIRA("ALVES_BANDEIRA", "Alves Bandeira", Regex("""\bALVES BANDEIRA\b""")),
    OZ_ENERGIA("OZ_ENERGIA", "OZ Energia", Regex("""\bOZ ENERGIA\b""")),
    PETROPRIX("PETROPRIX", "Petroprix", Regex("""\bPETROPRIX\b""")),
    PLENERGY("PLENERGY", "Plenergy", Regex("""\bPLENERGY\b""")),
    /** Posto de outra marca ou sem marca; o nome fica no texto livre do posto. */
    OTHER("OTHER", null, null);

    companion object {
        /** null para códigos desconhecidos (ex.: CSV de uma versão futura). */
        fun fromCode(code: String?): FuelBrand? = code?.let { c -> entries.firstOrNull { it.code == c.trim().uppercase() } }
    }
}

/** Combustível comprado. Guardado pelo [code]. */
enum class FuelType(val code: String) {
    GASOLINE_95("GASOLINA_95"),
    GASOLINE_98("GASOLINA_98"),
    DIESEL("GASOLEO"),
    LPG("GPL");

    companion object {
        fun fromCode(code: String?): FuelType? = code?.let { c -> entries.firstOrNull { it.code == c.trim().uppercase() } }
    }
}
