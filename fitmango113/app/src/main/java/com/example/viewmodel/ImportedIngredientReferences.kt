package com.example.viewmodel

/** Small, explicit alias table. No stemming of arbitrary product names or remote guesses.
 * USDA SR28, NDB 11215 (garlic) and 11677 (shallots), values per 100 g edible portion:
 * https://www.ars.usda.gov/ARSUserFiles/80400535/Data/SR/SR28/reports/sr28fg11.pdf
 * Olive oil reuses the existing local catalog (884 kcal, 100 g fat / 100 g).
 * A garlic clove is an ESTIMATE of 3 g, not a measured ingredient weight:
 * https://www.ars.usda.gov/arsuserfiles/80400525/data/hg72/hg72_2002.pdf
 */
object ImportedIngredientReferences {
    data class Reference(val key: String, val profile: IngredientSubstitutionSupport.KnownMacroProfile)
    private val garlic = Reference("garlic_raw", IngredientSubstitutionSupport.KnownMacroProfile(149f, 6.36f, 33.06f, .5f))
    private val shallot = Reference("shallot_raw", IngredientSubstitutionSupport.KnownMacroProfile(72f, 2.5f, 16.8f, .1f))
    private val olive = Reference("olive_oil", IngredientSubstitutionSupport.KnownMacroProfile(884f, 0f, 0f, 100f))
    // Reference approximations for generic ingredients; product-specific labels always win.
    // USDA SR28 dairy/eggs: NDB 01116 and 01123 (raw whole egg, edible portion).
    // https://www.ars.usda.gov/ARSUserFiles/80400525/Data/SR/SR28/reports/sr28fg01.pdf
    private val yogurt = Reference("yogurt_plain", IngredientSubstitutionSupport.KnownMacroProfile(61f, 3.47f, 4.66f, 3.25f))
    private val egg = Reference("egg_raw_whole", IngredientSubstitutionSupport.KnownMacroProfile(143f, 12.56f, .72f, 9.51f))
    // Representative white spelt flour; NOT wholemeal or a product label inferred from its name.
    // https://www.dovesfarm.co.uk/products/organic-white-spelt-flour-1kg
    private val spelt = Reference("spelt_white_flour", IngredientSubstitutionSupport.KnownMacroProfile(352f, 10f, 72f, 2f))
    // Representative baking powder. Actual brands can differ.
    // https://www.oetker.co.uk/products/p/baking-powder
    private val baking = Reference("baking_powder", IngredientSubstitutionSupport.KnownMacroProfile(78f, .1f, 19f, 0f))
    // USDA SR fruit table, NDB 09040 and 09003; fresh edible portion.
    private val banana = Reference("banana_raw", IngredientSubstitutionSupport.KnownMacroProfile(89f, 1.09f, 22.84f, .33f))
    private val apple = Reference("apple_raw", IngredientSubstitutionSupport.KnownMacroProfile(52f, .26f, 13.81f, .17f))
    // Additional generic USDA reference foods, per 100 g. These are estimates, not inferred labels.
    private val sugar = Reference("sugar_white", IngredientSubstitutionSupport.KnownMacroProfile(387f, 0f, 100f, 0f))
    private val butter = Reference("butter", IngredientSubstitutionSupport.KnownMacroProfile(717f, .85f, .06f, 81.11f))
    private val wheat = Reference("wheat_white_flour", IngredientSubstitutionSupport.KnownMacroProfile(364f, 10.33f, 76.31f, .98f))
    private val water = Reference("water", IngredientSubstitutionSupport.KnownMacroProfile(0f, 0f, 0f, 0f))
    private val aliases = mapOf(
        "czosnek" to garlic, "czosnku" to garlic, "czosnkiem" to garlic, "garlic" to garlic,
        "szalotka" to shallot, "szalotki" to shallot, "szalotke" to shallot,
        "cebula szalotka" to shallot, "cebuli szalotki" to shallot, "cebule szalotki" to shallot,
        "shallot" to shallot, "shallots" to shallot,
        "oliwa" to olive, "oliwy" to olive, "oliwe" to olive,
        "oliwa z oliwek" to olive, "oliwy z oliwek" to olive, "oliwe z oliwek" to olive,
        "oliwa extra virgin" to olive, "oliwy extra virgin" to olive,
        "oliwa z oliwek extra virgin" to olive, "oliwy z oliwek extra virgin" to olive,
        "olive oil" to olive, "extra virgin olive oil" to olive,
        "jogurt naturalny" to yogurt, "jogurtu naturalnego" to yogurt, "jogurtem naturalnym" to yogurt,
        "plain yogurt" to yogurt, "plain yoghurt" to yogurt,
        "jajko" to egg, "jajka" to egg, "jajek" to egg, "jajem" to egg,
        "jajko kurze" to egg, "jajka kurze" to egg, "jajek kurzych" to egg, "egg" to egg, "eggs" to egg,
        "maka orkiszowa jasna" to spelt, "maki orkiszowej jasnej" to spelt, "maka orkiszowa biala" to spelt,
        "maki orkiszowej bialej" to spelt, "white spelt flour" to spelt,
        "proszek do pieczenia" to baking, "proszku do pieczenia" to baking, "baking powder" to baking,
        "banan" to banana, "banana" to banana, "banany" to banana, "bananow" to banana, "bananas" to banana,
        "jablko" to apple, "jablka" to apple, "jablek" to apple, "apple" to apple, "apples" to apple,
        "cukier" to sugar, "cukru" to sugar, "cukier bialy" to sugar, "cukru bialego" to sugar, "white sugar" to sugar,
        "maslo" to butter, "masla" to butter, "maslem" to butter, "butter" to butter,
        "maka pszenna" to wheat, "maki pszennej" to wheat, "maka pszenna jasna" to wheat,
        "maki pszennej jasnej" to wheat, "plain flour" to wheat, "all purpose flour" to wheat,
        "woda" to water, "wody" to water, "woda gazowana" to water, "wody gazowanej" to water, "water" to water
    )
    private val catalogAliases = mapOf(
        "kaszy bulgur" to "Kasza bulgur", "kasza bulgur" to "Kasza bulgur",
        "kaszy gryczanej" to "Kasza gryczana", "kasza gryczana" to "Kasza gryczana",
        "ryzu bialego" to "Ryż biały", "ryz bialy" to "Ryż biały",
        "ryzu brazowego" to "Ryż brązowy", "ryz brazowy" to "Ryż brązowy",
        "komosy ryzowej" to "Komosa ryżowa (Quinoa)", "komosa ryzowa" to "Komosa ryżowa (Quinoa)",
        "makaronu pelnoziarnistego" to "Makaron pełnoziarnisty", "makaron pelnoziarnisty" to "Makaron pełnoziarnisty",
        "platkow owsianych" to "Płatki owsiane górskie", "platkow owsianych gorskich" to "Płatki owsiane górskie",
        "platki owsiane" to "Płatki owsiane górskie", "platki owsiane gorskie" to "Płatki owsiane górskie",
        "piersi z kurczaka" to "Pierś z kurczaka", "piers z kurczaka" to "Pierś z kurczaka",
        "piersi z indyka" to "Pierś z indyka", "piers z indyka" to "Pierś z indyka",
        "tofu naturalnego" to "Tofu naturalne", "tofu naturalne" to "Tofu naturalne",
        "lososia" to "Łosoś świeży", "losos" to "Łosoś świeży",
        "twarogu chudego" to "Twaróg chudy", "twarog chudy" to "Twaróg chudy",
        "jogurtu skyr naturalnego" to "Jogurt Skyr naturalny", "jogurt skyr naturalny" to "Jogurt Skyr naturalny",
        "mleka migdalowego nieslodzonego" to "Mleko migdałowe niesłodzone", "mleko migdalowe nieslodzone" to "Mleko migdałowe niesłodzone",
        "mleka kokosowego" to "Mleko kokosowe", "mleko kokosowe" to "Mleko kokosowe",
        "batatow" to "Bataty (słodkie ziemniaki)", "bataty" to "Bataty (słodkie ziemniaki)",
        "chleba zytniego razowego" to "Chleb żytni razowy", "chleb zytni razowy" to "Chleb żytni razowy"
    )
    private val catalogReferences by lazy {
        IngredientSubstitutionSupport.defaultLocalCatalog.mapNotNull { candidate ->
            val kcal = candidate.caloriesPer100g ?: return@mapNotNull null
            candidate.name to Reference("catalog:${candidate.name}", IngredientSubstitutionSupport.KnownMacroProfile(kcal,
                candidate.proteinPer100g, candidate.carbsPer100g, candidate.fatPer100g))
        }.toMap()
    }
    private val rawPreparation = setOf("surowy", "surowego", "surowa", "surowej", "swiezy", "swiezego", "swieza", "swiezej",
        "posiekany", "posiekanego", "posiekana", "posiekanej", "posiekane", "obrany", "obranego", "obrana", "obranej", "drobno", "dojrzaly", "dojrzalego", "dojrzale", "ripe", "raw", "fresh", "chopped", "peeled")
    private val punctuation = Regex("[^a-z0-9 ]")
    private val spaces = Regex("\\s+")
    fun find(name: String): Reference? {
        val words = ProduceSupport.normalize(name).replace(punctuation, " ")
            .trim().split(spaces).filter { it !in rawPreparation }
        val key = words.joinToString(" ")
        return aliases[key] ?: catalogAliases[key]?.let { catalogReferences[it] }
    }
    fun sameFood(a: String, b: String): Boolean {
        val first = find(a) ?: return false
        return first.key == find(b)?.key
    }
    fun isCloveUnit(unit: String) = ProduceSupport.normalize(unit.trim().removeSuffix(".")) in
        setOf("zabek", "zabki", "zabkow", "clove", "cloves")
    fun isPieceUnit(unit: String) = ProduceSupport.normalize(unit.trim().removeSuffix(".")) in
        setOf("szt", "sztuka", "sztuki", "sztuk", "piece", "pieces", "unit", "units")
    fun sameCountUnit(a: String, b: String) = isCloveUnit(a) && isCloveUnit(b) || isPieceUnit(a) && isPieceUnit(b)
    fun massEstimateLabel(name: String, unit: String): String? = when {
        find(name)?.key == "garlic_raw" && isCloveUnit(unit) -> "szacunek: 1 ząbek ≈ 3 g"
        find(name)?.key == "egg_raw_whole" && isPieceUnit(unit) -> "szacunek: 1 szt. ≈ 50 g części jadalnej"
        else -> null
    }
    fun estimatedGrams(name: String, quantity: Float, unit: String): Float? {
        if (!quantity.isFinite() || quantity <= 0f) return null
        val perUnit = when {
            find(name)?.key == "garlic_raw" && isCloveUnit(unit) -> 3f
            find(name)?.key == "egg_raw_whole" && isPieceUnit(unit) -> 50f
            else -> return null
        }
        return (quantity * perUnit).takeIf { it.isFinite() }
    }
}
