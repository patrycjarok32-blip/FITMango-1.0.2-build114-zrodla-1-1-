package com.example.viewmodel

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.callGemini
import com.example.data.AppDatabase
import com.example.data.DietaRepository
import com.example.data.RecipeEntity
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class PdfRecipeExtractorTest {

    @org.junit.Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        com.example.ai.SecureKeyStorage.init(app)
        com.example.ai.SecureKeyStorage.setTestingMode(true)
        com.example.ai.SecureKeyStorage.saveKey("FAKE_TESTING_KEY")
    }

    private data class SeedRecipeSpec(
        val title: String,
        val servings: String,
        val calories: Int,
        val protein: Int,
        val carbs: Int,
        val fat: Int,
        val ingredients: List<String>,
        val steps: List<String>,
        val culinaryNotes: String
    )

    private val thirtyRecipesCatalog = listOf(
        SeedRecipeSpec(
            title = "Placki z jablkami",
            servings = "2",
            calories = 420, protein = 18, carbs = 58, fat = 12,
            ingredients = listOf("150 g Jablka szara reneta", "120 g Maka orkiszowa jasna", "150 g Jogurt naturalny gesty", "2 szt Jajka kurze", "5 g Cynamon mielony", "10 ml Olej rzepakowy"),
            steps = listOf("Jablka obierz i zetrzyj na tarce o grubych oczkach.", "Wymieszaj jajka z jogurtem naturalnym, maka orkiszowa i cynamonem na gladka mase.", "Dodaj starte jablka i delikatnie polacz szpatulka.", "Smaz niewielkie placuszki na rozgrzanym oleju na zloty kolor z obu stron."),
            culinaryNotes = "Klasyczne domowe placuszki jablkowe z aromatycznym cynamonem, idealne na cieple sniadanie."
        ),
        SeedRecipeSpec(
            title = "Placki z bananem",
            servings = "2",
            calories = 450, protein = 20, carbs = 62, fat = 14,
            ingredients = listOf("160 g Dojrzaly banan", "100 g Platki owsiane gorskie", "150 g Twarog chudy", "2 szt Jajka kurze", "15 g Maslo orzechowe", "5 g Proszek do pieczenia"),
            steps = listOf("Banana rozgniec widelcem na gladkie puree w duzej misce.", "Zblenduj platki owsiane, twarog chudy, jajka oraz maslo orzechowe na jednolita mase.", "Polacz mase z rozgniecionym bananem i proszkiem do pieczenia.", "Smaz na suchej patelni nieprzywierajacej po 3 minuty z kazdej strony."),
            culinaryNotes = "Puszyste placki bananowo-twarogowe o wysokiej zawartosci bialka i naturalnej slodyczy banana."
        ),
        SeedRecipeSpec(
            title = "Zupa pomidorowa klasyczna",
            servings = "4",
            calories = 340, protein = 22, carbs = 44, fat = 8,
            ingredients = listOf("800 ml Bulion drobiowo warzywny", "400 g Passata pomidorowa", "120 g Makaron pelnoziarnisty swiderki", "150 g Piers z kurczaka gotowana", "80 g Marchewka", "15 g Natka pietruszki"),
            steps = listOf("W garnku zagotuj bulion z pokrojona w plasterki marchewka oraz miesem drobiowym.", "Wlej passate pomidorowa i gotuj na wolnym ogniu przez 15 minut.", "Osobno ugotuj makaron pelnoziarnisty al dente.", "Podawaj czysta zupe pomidorowa z makaronem i posiekana natka pietruszki."),
            culinaryNotes = "Tradycyjna czysta zupa pomidorowa na domowym wywarze z makaronem swiderki i kurczakiem."
        ),
        SeedRecipeSpec(
            title = "Zupa pomidorowa krem",
            servings = "3",
            calories = 390, protein = 14, carbs = 38, fat = 19,
            ingredients = listOf("900 g Pomidory malinowe pieczone", "120 g Mozzarella biala", "30 g Pestki dyni", "20 ml Oliwa z oliwek extra virgin", "100 g Cebula czerwona", "15 g Swieza bazylia", "3 zabki Czosnek"),
            steps = listOf("Pomidory malinowe, cebule i czosnek skrop oliwa i piecz w piekarniku w 200 stopniach przez 30 minut.", "Przeloz upieczone warzywa do garnka, dodaj swieza bazylie i zblenduj na aksamitny gesty krem.", "Podawaj krem z szarpana mozzarella i uprazonymi pestkami dyni na wierzchu."),
            culinaryNotes = "Aksamitny gesty krem z pieczonych pomidorow malinowych z mozzarella i chrupiacymi pestkami dyni."
        ),
        SeedRecipeSpec(
            title = "Owsianka krolewska z malinami i migdalami",
            servings = "1",
            calories = 410, protein = 19, carbs = 54, fat = 13,
            ingredients = listOf("60 g Platki owsiane gorskie", "200 ml Mleko 1.5 procent", "100 g Maliny swieze", "20 g Migdaly platki", "15 g Odzywka bialkowa waniliowa", "10 g Nasiona chia"),
            steps = listOf("Platki owsiane i nasiona chia zalej mlekiem w rondelku i gotuj 5 minut na malym ogniu.", "Zdejmij z ognia, przestudz przez chwile i wymieszaj z odzywka bialkowa.", "Przeloz do miski, udekoruj malinami i uprazonymi platkami migdalow."),
            culinaryNotes = "Sycace sniadanie bogate w blonnik rozpuszczalny oraz kwasy omega z nasion chia."
        ),
        SeedRecipeSpec(
            title = "Szakszuka ze szpinakiem i serem feta",
            servings = "2",
            calories = 460, protein = 26, carbs = 18, fat = 31,
            ingredients = listOf("4 szt Jajka z wolnego wybiegu", "400 g Pomidory krojone z puszki", "100 g Szpinak baby swiezy", "60 g Ser feta grecki", "60 g Cebula dymka", "10 ml Oliwa z oliwek", "3 g Kmin rzymski"),
            steps = listOf("Na oliwie zeszklij posiekana dymke z kminem rzymskim.", "Dodaj szpinak baby i dus przez minute az zmieknie, nastepnie wlej pomidory krojone.", "Gdy sos zgestnieje, zrob w nim 4 wglebienia i wbij jajka.", "Posyp pokruszona feta, przykryj pokrywka i dus 5 minut az bialka sie zetna."),
            culinaryNotes = "Aromatyczna szakszuka w stylu srodziemnomorskim ze swiezym szpinakiem i slona feta."
        ),
        SeedRecipeSpec(
            title = "Kurczak tikka masala z ryzem basmati",
            servings = "2",
            calories = 560, protein = 42, carbs = 58, fat = 16,
            ingredients = listOf("300 g Piers z kurczaka", "120 g Ryz basmati suchy", "150 ml Mleczko kokosowe lekkie", "200 g Passata pomidorowa", "80 g Jogurt grecki", "10 g Przyprawa garam masala", "10 g Imbir swiezy"),
            steps = listOf("Pokroj kurczaka w kostke i zamarynuj w jogurcie z garam masala oraz tartym imbirem.", "Obsmaz mieso na rozgrzanej patelni, nastepnie wlej passate i mleczko kokosowe.", "Dus pod przykryciem przez 15 minut az sos zgestnieje.", "Podawaj z ugotowanym na sypko ryzem basmati."),
            culinaryNotes = "Rozgrzewajace danie indyjskie o kremowym sosie pomidorowo-kokosowym i wysokiej podazy bialka."
        ),
        SeedRecipeSpec(
            title = "Losos pieczony w glazurze miodowo musztardowej",
            servings = "2",
            calories = 580, protein = 38, carbs = 42, fat = 28,
            ingredients = listOf("260 g Filet z lososia atlantyckiego", "300 g Ziemniaki mlode", "200 g Brokul swiezy", "15 g Musztarda francuska ziarnista", "12 g Miod pszczeli", "15 ml Sok z cytryny", "10 ml Oliwa z oliwek"),
            steps = listOf("Wymieszaj musztarde francuska z miodem i sokiem z cytryny na glazurze.", "Posmaruj filety z lososia glazura i piecz przez 18 minut w 190 stopniach.", "Ziemniaki ugotuj w mundurkach, a brokul ugotuj na parze al dente.", "Podawaj upieczonego lososia z ziemniakami i zielonym brokulem."),
            culinaryNotes = "Bogaty w kwasy tluszczowe omega-3 obiad rybny z soczysta glazura miodowo-musztardowa."
        ),
        SeedRecipeSpec(
            title = "Salatka grecka z ciecierzyca i komosa ryzowa",
            servings = "2",
            calories = 490, protein = 18, carbs = 52, fat = 22,
            ingredients = listOf("100 g Komosa ryzowa quinoa", "160 g Ciecierzyca gotowana", "150 g Ogorek zielony", "150 g Pomidorki koktajlowe", "70 g Ser feta", "40 g Oliwki czarne kalamata", "20 ml Oliwa z oliwek"),
            steps = listOf("Ugotuj komose ryzowa w lekko osolonej wodzie przez 15 minut i przestudz.", "Pokroj ogorka w kostke, a pomidorki koktajlowe i oliwki przekroj na polowki.", "Wymieszaj komose z odcedzona ciecierzyca, warzywami i pokruszona feta.", "Skrop oliwa z oliwek i dopraw suszonym oregano."),
            culinaryNotes = "Pelnowartosciowa wegetarianska salatka obiadowa dostarczajaca kompletu aminokwasow."
        ),
        SeedRecipeSpec(
            title = "Makaron penne ze szpinakiem i suszonymi pomidorami",
            servings = "2",
            calories = 520, protein = 28, carbs = 64, fat = 16,
            ingredients = listOf("140 g Makaron penne pelnoziarnisty", "200 g Filet z indyka", "120 g Szpinak swiezy", "60 g Suszone pomidory z zalewy", "100 g Serek ricotta", "2 zabki Czosnek", "15 g Parmezan"),
            steps = listOf("Ugotuj makaron penne al dente w osolonej wodzie.", "Indyka pokroj w paski i usmaz z posiekanym czosnkiem i paskami suszonych pomidorow.", "Dodaj szpinak oraz serek ricotta, podlej odrobina wody z makaronu.", "Polacz z ugotowanym makaronem i posyp tartym parmezanem."),
            culinaryNotes = "Szybki wloski obiad z chudym miesem indyka i kremowym sosem na bazie ricotty."
        ),
        SeedRecipeSpec(
            title = "Chili sin carne z czerwona fasola i kukurydza",
            servings = "3",
            calories = 440, protein = 19, carbs = 68, fat = 9,
            ingredients = listOf("240 g Fasola czerwona konserwowa", "140 g Soczewica czerwona sucha", "150 g Kukurydza zlota", "500 g Passata pomidorowa", "150 g Papryka czerwona", "100 g Cebula zolta", "5 g Wedzona papryka"),
            steps = listOf("W garnku podsmaz posiekana cebule i papryke czerwona z wedzona papryka i kminem.", "Wsyp wyplukana soczewice czerwona, zalej passata pomidorowa i 300 ml wody.", "Gotuj 18 minut az soczewica zmieknie, nastepnie dodaj fasole i kukurydze.", "Podgrzewaj jeszcze 5 minut i dopraw kolendra."),
            culinaryNotes = "Geste i niezwykle sycace danie jednogarnkowe bogate w bialko roslinne i zelazo."
        ),
        SeedRecipeSpec(
            title = "Twarozek wiosenny z rzodkiewka i szczypiorkiem",
            servings = "1",
            calories = 350, protein = 29, carbs = 34, fat = 9,
            ingredients = listOf("150 g Twarog poltlusty", "50 g Jogurt naturalny", "80 g Rzodkiewka swieza", "20 g Szczypiorek siekany", "70 g Chleb zytni na zakwasie", "10 g Slonecznik luskany"),
            steps = listOf("Twarog rozgniec widelcem z jogurtem naturalnym na kremowa konsystencje.", "Rzodkiewke zetrzyj na tarce lub pokroj w drobna kostke i dodaj ze szczypiorkiem do sera.", "Posyp uprazonymi pestkami slonecznika i podawaj z kromkami chleba zytniego."),
            culinaryNotes = "Lekkie wysokobialkowe sniadanie lub kolacja z chrupiacymi wiosennymi warzywami."
        ),
        SeedRecipeSpec(
            title = "Koktajl zielony ze szpinakiem kiwi i awokado",
            servings = "1",
            calories = 290, protein = 9, carbs = 36, fat = 12,
            ingredients = listOf("60 g Szpinak baby", "70 g Awokado dojrzale", "120 g Kiwi obrane", "100 g Banan", "200 ml Napoj migdalowy nieslodzony", "15 ml Sok z limonki"),
            steps = listOf("Umiesc w kielichu blendera umyty szpinak, obrane kiwi, banana oraz miazsz z awokado.", "Wlej napoj migdalowy i swiezo wycisniety sok z limonki.", "Blenduj na wysokich obrotach przez 45 sekund na aksamitne zielone smoothie."),
            culinaryNotes = "Odswiezajacy koktajl witaminowy bogaty w potas, witamine C i zdrowe jednonienasycone kwasy tluszczowe."
        ),
        SeedRecipeSpec(
            title = "Gulasz wolowy z kasza gryczana i papryka",
            servings = "3",
            calories = 550, protein = 40, carbs = 54, fat = 18,
            ingredients = listOf("350 g Wolowina udziec", "150 g Kasza gryczana prazona", "200 g Papryka zolta i czerwona", "150 g Pieczarki swieze", "100 g Cebula", "400 ml Bulion wolowy", "15 ml Olej rzepakowy"),
            steps = listOf("Wolowine pokroj w kostke i mocno obsmaż na rozgrzanym oleju z cebula.", "Zalej goracym bulionem wolowym, dodaj lisc laurowy i dus pod przykryciem przez 60 minut.", "Dodaj pokrojone pieczarki i papryke, dus kolejne 20 minut az mieso bedzie kruche.", "Podawaj z ugotowana na sypko kasza gryczana."),
            culinaryNotes = "Tradycyjny gulasz wolowy bedacy doskonałym zrodlem dobrze przyswajalnego zelaza hemowego."
        ),
        SeedRecipeSpec(
            title = "Nalesniki pelnoziarniste z serem i jagodami",
            servings = "2",
            calories = 470, protein = 27, carbs = 62, fat = 12,
            ingredients = listOf("100 g Maka pszenna pelnoziarnista", "150 ml Mleko 2 procent", "100 ml Woda gazowana", "1 szt Jajko", "180 g Twarog chudy", "120 g Jagody lesne", "15 g Ksylitol"),
            steps = listOf("Wymieszaj make pelnoziarnista, mleko, wode gazowana i jajko na ciasto nalesnikowe.", "Usmaż cienkie nalesniki na dobrze rozgrzanej patelni.", "Twarog utrzyj z ksylitolem i polowa jagod, posmaruj nalesniki i zwin w rulony.", "Podawaj posypane pozostalymi swiezymi jagodami."),
            culinaryNotes = "Zdrowa wersja nalesnikow z serem bez bialego cukru, slodzona ksylitolem."
        ),
        SeedRecipeSpec(
            title = "Bowl z pieczonym tofu batatem i sosem orzechowym",
            servings = "2",
            calories = 530, protein = 24, carbs = 56, fat = 23,
            ingredients = listOf("200 g Tofu naturalne", "250 g Batat obrany", "100 g Ryz brazowy", "100 g Edamame luskane", "30 g Maslo orzechowe 100 procent", "15 ml Sos sojowy o obnizonej zawartosci soli", "10 g Sezam bialy"),
            steps = listOf("Tofu i batata pokroj w kostke, skrop sosem sojowym i upiecz w 200 stopniach przez 25 minut.", "Ugotuj ryz brazowy oraz ziarna soi edamame.", "Wymieszaj maslo orzechowe z ciepla woda i sosem sojowym na gladki dressing.", "Uloz w misce ryz, bataty, chrupiace tofu i edamame, polej sosem i posyp sezamem."),
            culinaryNotes = "Kolorowy roslinny buddha bowl bogaty w beta-karoten z batatow oraz bialko sojowe."
        ),
        SeedRecipeSpec(
            title = "Dorsz pieczony na parze z kasza bulgur i porem",
            servings = "2",
            calories = 430, protein = 36, carbs = 48, fat = 9,
            ingredients = listOf("300 g Polędwica z dorsza swieza", "120 g Kasza bulgur", "150 g Por biala czesc", "100 g Marchewka", "15 g Maslo klarowane", "15 ml Sok z cytryny", "10 g Koperek swiezy"),
            steps = listOf("Pora pokroj w polplasterki, a marchewke w slupki i poddus na masle klarowanym.", "Na warzywach uloz przyprawiona poledwice z dorsza skropiona sokiem z cytryny.", "Przykryj i dus na parze z warzyw przez 12 minut.", "Podawaj z ugotowana kasza bulgur i obficie posyp swiezym koperkiem."),
            culinaryNotes = "Lekkostrawny obiad o niskiej zawartosci tluszczu i bardzo wysokiej zawartosci pelnowartosciowego bialka."
        ),
        SeedRecipeSpec(
            title = "Omlet biszkoptowy z borowkami i maslem migdalowym",
            servings = "1",
            calories = 415, protein = 23, carbs = 34, fat = 20,
            ingredients = listOf("3 szt Jajka kurze", "30 g Maka owsiana", "80 g Borowki amerykanskie", "15 g Maslo migdalowe", "40 g Jogurt grecki", "5 g Erytrytol"),
            steps = listOf("Oddziel bialka od zoltek i ubij bialka na sztywna piane z erytrytolem.", "Delikatnie wmieszaj zoltka oraz przesiana make owsiana do piany.", "Przeloz mase na rozgrzana patelnie i smaz pod przykryciem na malym ogniu z obu stron.", "Podawaj z jogurtem greckim, borowkami i maslem migdalowym."),
            culinaryNotes = "Puszysty słodki omlet sniadaniowy bez dodatku cukru."
        ),
        SeedRecipeSpec(
            title = "Zupa krem z dyni hokkaido z imbirem i mlekiem kokosowym",
            servings = "3",
            calories = 330, protein = 9, carbs = 36, fat = 16,
            ingredients = listOf("700 g Dynia hokkaido ze skorka", "150 g Ziemniaki", "200 ml Mleczko kokosowe", "500 ml Bulion warzywny", "15 g Imbir swiezy starty", "30 g Pestki slonecznika", "5 g Kurkuma"),
            steps = listOf("Dynie hokkaido i ziemniaki pokroj w kostke i ugotuj w bulionie warzywnym z imbirem i kurkuma.", "Gdy warzywa zmiekna, wlej mleczko kokosowe i zblenduj zupe na jedwabisty krem.", "Podawaj posypana uprazonymi na suchej patelni pestkami slonecznika."),
            culinaryNotes = "Aromatyczny jesienny krem z dyni o dzialaniu przeciwzapalnym dzieki kurkumie i imbirowi."
        ),
        SeedRecipeSpec(
            title = "Tortilla pelnoziarnista z kurczakiem awokado i salsa",
            servings = "2",
            calories = 495, protein = 34, carbs = 46, fat = 18,
            ingredients = listOf("2 szt Tortilla pelnoziarnista", "220 g Piers z kurczaka", "100 g Awokado", "150 g Pomidor malinowy", "50 g Cebula czerwona", "60 g Salata rzymska", "40 g Jogurt naturalny"),
            steps = listOf("Kurczaka pokroj w paski, przypraw papryka slodka i usmaz na grillowej patelni.", "Pomidora i czerwona cebule posiekaj drobno na swieza salse.", "Na tortilli rozsmaruj jogurt, uloz liscie salaty rzymskiej, plastry awokado, kurczaka i salse.", "Zwin ciasno w rulon i podpiecz przez 2 minuty na suchej patelni."),
            culinaryNotes = "Szybki lunch do pracy w formie wrapa pelnoziarnistego z grillowanym kurczakiem."
        ),
        SeedRecipeSpec(
            title = "Kasza jaglana na slodko z pieczonym jablkiem i orzechami wloskimi",
            servings = "1",
            calories = 435, protein = 13, carbs = 64, fat = 14,
            ingredients = listOf("60 g Kasza jaglana sucha", "200 ml Mleko roslinne owsiane", "150 g Jablko ligol", "20 g Orzechy wloskie", "5 g Cynamon cejlonski", "10 g Rodzynki"),
            steps = listOf("Kasze jaglana dokladnie przelej wrzatkiem na sicie aby usunac goryczke, po czym ugotuj na mleku owsianym.", "Jablko pokroj w czastki, posyp cynamonem i podpiecz przez 12 minut.", "Uloz goraca jaglanke w misce, dodaj pieczone jablko, rodzynki i posiekane orzechy wloskie."),
            culinaryNotes = "Lekkostrawna i zasadotworcza jaglanka z orzechami bogatymi w kwasy omega-3."
        ),
        SeedRecipeSpec(
            title = "Kotleciki drobiowo cukiniowe pieczone w piekarniku",
            servings = "3",
            calories = 410, protein = 39, carbs = 28, fat = 14,
            ingredients = listOf("450 g Mieso mielone z piersi indyka", "250 g Cukinia zielona", "1 szt Jajko", "40 g Otreby owsiane", "80 g Marchewka", "15 g Koperek i pietruszka", "2 zabki Czosnek"),
            steps = listOf("Cukinie i marchewke zetrzyj na tarce, cukinie posol i odcisnij z nadmiaru soku.", "Wymieszaj mieso mielone z indyka z odcisnieta cukinia, marchewka, jajkiem, otrebami i ziolami.", "Uformuj zgrabne kotleciki, uloz na papierze do pieczenia i piecz 25 minut w 190 stopniach."),
            culinaryNotes = "Soczyste pieczone kotleciki drobiowe bez smazenia w glebokim tluszczu."
        ),
        SeedRecipeSpec(
            title = "Salatka z tunczykiem jajkiem i fasolka szparagowa",
            servings = "2",
            calories = 395, protein = 35, carbs = 22, fat = 18,
            ingredients = listOf("160 g Tunczyk w sosie wlasnym", "2 szt Jajka ugotowane na twardo", "200 g Fasolka szparagowa zielona", "100 g Pomidorki koktajlowe", "60 g Mix salat", "15 ml Oliwa z oliwek", "5 g Musztarda dijon"),
            steps = listOf("Fasolke szparagowa ugotuj w osolonej wodzie al dente i zahartuj zimna woda.", "Na talerzu rozloz mix salat, ugotowana fasolke, czastki jajek oraz odsaczony tunczyk.", "Wymieszaj oliwe z musztarda dijon i polej salatke przed podaniem."),
            culinaryNotes = "Inspirowana salatka nicejska propozycja kolacyjna o wysokiej zawartosci bialka."
        ),
        SeedRecipeSpec(
            title = "Risotto z borowikami i natka pietruszki",
            servings = "2",
            calories = 480, protein = 15, carbs = 68, fat = 16,
            ingredients = listOf("140 g Ryz arborio do risotto", "250 g Borowiki lub podgrzybki", "600 ml Bulion warzywny goracy", "80 g Szalotka", "25 g Parmezan tarty", "15 g Maslo ekstra", "20 g Natka pietruszki"),
            steps = listOf("Szalotke i oczyszczone borowiki podsmaz na polowie masla.", "Wsyp ryz arborio, smaz minute i stopniowo dolewaj po chochli goracego bulionu ciagle mieszajac.", "Gdy ryz bedzie kremowy i al dente, wmieszaj reszte masla, parmezan oraz natke pietruszki."),
            culinaryNotes = "Aromatyczne wloskie risotto z lesnymi grzybami i swieza pietruszka."
        ),
        SeedRecipeSpec(
            title = "Pudding chia na mleku kokosowym z musem mango",
            servings = "2",
            calories = 310, protein = 8, carbs = 32, fat = 17,
            ingredients = listOf("50 g Nasiona chia", "300 ml Mleko kokosowe lekkie", "200 g Mango dojrzale miazsz", "10 ml Sok z limonki", "10 g Wiorki kokosowe", "5 g Miod"),
            steps = listOf("Nasiona chia dokladnie wymieszaj z mlekiem kokosowym i miodem, odstaw do lodowki na minimum 3 godziny.", "Miazsz dojrzalego mango zblenduj z sokiem z limonki na gladki zlocisty mus.", "Na wierzch schlodzonego puddingu chia wyloz mus mango i posyp wiorkami kokosowymi."),
            culinaryNotes = "Egzotyczny deser lub drugie sniadanie w stylu MangoFit bez glutenu."
        ),
        SeedRecipeSpec(
            title = "Zapiekanka makaronowa z brokulem kurczakiem i mozzarella",
            servings = "4",
            calories = 510, protein = 38, carbs = 50, fat = 17,
            ingredients = listOf("240 g Makaron swiderki pelnoziarnisty", "400 g Piers z kurczaka", "400 g Brokul", "150 g Mozzarella tarta", "200 g Jogurt grecki", "2 szt Jajka", "2 zabki Czosnek"),
            steps = listOf("Ugotuj makaron al dente, a rozyczki brokula blanszuj przez 3 minuty we wrzatku.", "Kurczaka pokroj w kostke i podsmaz z czosnkiem i ziolami prowansalskimi.", "W naczyniu zaroodpornym wymieszaj makaron, brokul i kurczaka, zalej sosem z jogurtu i jajek.", "Posyp mozzarella i zapiekaj 22 minuty w 190 stopniach."),
            culinaryNotes = "Rodzinna zapiekanka obiadowa w odchudzonej wersji z sosem jogurtowym zamiast smietany."
        ),
        SeedRecipeSpec(
            title = "Kanapki z pasta z wędzonej makreli i ogorkiem kiszonym",
            servings = "2",
            calories = 420, protein = 28, carbs = 38, fat = 17,
            ingredients = listOf("160 g Makrela wedzona bez osci", "100 g Twarog poltlusty", "30 g Jogurt naturalny", "100 g Ogorek kiszony", "140 g Chleb zytni razowy", "20 g Szczypiorek"),
            steps = listOf("Mieso wedzonej makreli dokladnie oczysc z osci i rozgniec widelcem z twarogiem oraz jogurtem.", "Dodaj drobno pokrojonego ogorka kiszonego i posiekany szczypiorek, dopraw pieprzem.", "Rozsmaruj paste na kromkach chleba zytniego razowego."),
            culinaryNotes = "Wyrazista pasta rybna bogata w kwasy omega-3 oraz witamine D."
        ),
        SeedRecipeSpec(
            title = "Polędwiczki wieprzowe w sosie kurkowym z kasza peczak",
            servings = "2",
            calories = 515, protein = 41, carbs = 52, fat = 15,
            ingredients = listOf("300 g Poledwiczka wieprzowa", "120 g Kasza jeczmienna peczak", "200 g Kurki swieze", "80 g Cebula", "100 g Smietanka 12 procent", "10 ml Oliwa z oliwek", "15 g Koperek"),
            steps = listOf("Poledwiczke pokroj w medaliony i krotko obsmaż z obu stron na rozgrzanej oliwie.", "Dodaj posiekana cebule oraz oczyszczone kurki, dus razem przez 10 minut.", "Wlej smietanke 12 procent i posyp koperkiem, podawaj z ugotowana kasza peczak."),
            culinaryNotes = "Delikatne i chude mieso z poledwiczki wieprzowej w aromatycznym sosie z kurek."
        ),
        SeedRecipeSpec(
            title = "Hummus domowy ze slupkami swiezych warzyw i pita",
            servings = "2",
            calories = 385, protein = 14, carbs = 44, fat = 16,
            ingredients = listOf("240 g Ciecierzyca ugotowana", "30 g Pasta sezamowa tahini", "20 ml Sok z cytryny", "15 ml Oliwa z oliwek", "150 g Marchewka i seler naciowy", "80 g Chlebek pita pelnoziarnisty", "1 zabek Czosnek"),
            steps = listOf("Ciecierzyce zblenduj z pasta tahini, sokiem z cytryny, czosnkiem, oliwa i 3 lyzkami zimnej wody na gladki hummus.", "Marchewke oraz seler naciowy pokroj w dlugie chrupiace slupki.", "Podawaj hummus ze slupkami warzyw i cieplym chlebkiem pita."),
            culinaryNotes = "Klasyczna bliskowschodnia przekaska bogata w blonnik i wapn z pasty sezamowej."
        ),
        SeedRecipeSpec(
            title = "Pieczony pstrag teczowy z cytryna tymiankiem i surówka z kiszonej kapusty",
            servings = "2",
            calories = 465, protein = 40, carbs = 34, fat = 18,
            ingredients = listOf("360 g Pstrag teczowy filet", "250 g Ziemniaki", "200 g Kapusta kiszona", "60 g Marchewka", "15 ml Olej lniany", "20 g Cytryna plastry", "5 g Tymianek swiezy"),
            steps = listOf("Filety z pstraga obloz plastrami cytryny oraz galazkami tymianku i upiecz w 190 stopniach przez 20 minut.", "Kapuste kiszona wymieszaj ze starta marchewka i olejem lnianym na zimno.", "Podawaj pieczonego pstraga z gotowanymi ziemniakami i surowka."),
            culinaryNotes = "Zdrowy polski obiad rybny z probiotyczna kiszona kapusta i olejem lnianym."
        )
    )

    private fun String.toWinAnsiSafe(): String {
        val map = mapOf(
            'ą' to 'a', 'ć' to 'c', 'ę' to 'e', 'ł' to 'l', 'ń' to 'n', 'ó' to 'o', 'ś' to 's', 'ź' to 'z', 'ż' to 'z',
            'Ą' to 'A', 'Ć' to 'C', 'Ę' to 'E', 'Ł' to 'L', 'Ń' to 'N', 'Ó' to 'O', 'Ś' to 'S', 'Ź' to 'Z', 'Ż' to 'Z'
        )
        return buildString(length) {
            for (ch in this@toWinAnsiSafe) {
                append(map[ch] ?: ch)
            }
        }
    }

    private fun generateLargeRecipePdfBytes(): ByteArray {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(context)
        val doc = PDDocument()
        // 15 pages, 2 recipes per page = 30 recipes total
        val recipesPerPage = 2
        val totalPages = thirtyRecipesCatalog.size / recipesPerPage

        for (pageIdx in 0 until totalPages) {
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)

            PDPageContentStream(doc, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA_BOLD, 12f)
                stream.newLineAtOffset(40f, 800f)
                stream.showText("MANGOFIT KOMPLETNY JADLOSPIS - STRONA ${pageIdx + 1} Z $totalPages".toWinAnsiSafe())
                stream.endText()

                var currentY = 770f
                for (offset in 0 until recipesPerPage) {
                    val recipeIndex = pageIdx * recipesPerPage + offset
                    val spec = thirtyRecipesCatalog[recipeIndex]

                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA_BOLD, 11f)
                    stream.newLineAtOffset(40f, currentY)
                    stream.showText("PRZEPIS ${recipeIndex + 1}: ${spec.title}".toWinAnsiSafe())
                    stream.endText()
                    currentY -= 16f

                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 9.5f)
                    stream.newLineAtOffset(40f, currentY)
                    stream.showText("Porcje: ${spec.servings} | Wartosci odzywcze: ${spec.calories} kcal, Bialko: ${spec.protein}g, Weglowodany: ${spec.carbs}g, Tluszcz: ${spec.fat}g".toWinAnsiSafe())
                    stream.endText()
                    currentY -= 14f

                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 9f)
                    stream.newLineAtOffset(40f, currentY)
                    stream.showText("Opis dietetyczny: ${spec.culinaryNotes}".toWinAnsiSafe())
                    stream.endText()
                    currentY -= 15f

                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA_BOLD, 9.5f)
                    stream.newLineAtOffset(40f, currentY)
                    stream.showText("Skladniki:")
                    stream.endText()
                    currentY -= 13f

                    for (ing in spec.ingredients) {
                        stream.beginText()
                        stream.setFont(PDType1Font.HELVETICA, 9f)
                        stream.newLineAtOffset(50f, currentY)
                        stream.showText("- $ing (swiezy produkt wysokiej jakosci do przygotowania posilku)".toWinAnsiSafe())
                        stream.endText()
                        currentY -= 12f
                    }

                    currentY -= 4f
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA_BOLD, 9.5f)
                    stream.newLineAtOffset(40f, currentY)
                    stream.showText("Instrukcja przygotowania krok po kroku:")
                    stream.endText()
                    currentY -= 13f

                    spec.steps.forEachIndexed { stepIdx, stepText ->
                        stream.beginText()
                        stream.setFont(PDType1Font.HELVETICA, 9f)
                        stream.newLineAtOffset(50f, currentY)
                        stream.showText("Krok ${stepIdx + 1}: $stepText".toWinAnsiSafe())
                        stream.endText()
                        currentY -= 12f
                    }

                    currentY -= 22f
                }
            }
        }

        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        return out.toByteArray()
    }

    @Test
    fun deduplication_preservesSimilarNamedDistinctRecipes_andMergesTrueDuplicates() {
        val applePancakes = ParsedRecipe(
            title = "Placki z jabłkami",
            servings = "2",
            ingredients = "150 g Jabłka\n120 g Mąka orkiszowa\n150 g Jogurt naturalny\n2 szt Jajka",
            instructions = "1. Zetrzyj jabłka.\n2. Usmaż placki.",
            calories = 420, protein = 18, carbs = 58, fat = 12,
            source = "Jadlospis.pdf"
        )
        val bananaPancakes = ParsedRecipe(
            title = "Placki z bananem",
            servings = "2",
            ingredients = "160 g Banan\n100 g Płatki owsiane\n150 g Twaróg chudy\n2 szt Jajka",
            instructions = "1. Rozgnieć banana.\n2. Usmaż placki.",
            calories = 450, protein = 20, carbs = 62, fat = 14,
            source = "Jadlospis.pdf"
        )
        val tomatoSoupClassic = ParsedRecipe(
            title = "Zupa pomidorowa klasyczna",
            servings = "4",
            ingredients = "800 ml Bulion\n400 g Passata pomidorowa\n120 g Makaron świderki\n150 g Kurczak",
            instructions = "1. Zagotuj bulion z passatą.\n2. Podawaj z makaronem.",
            calories = 340, protein = 22, carbs = 44, fat = 8,
            source = "Jadlospis.pdf"
        )
        val tomatoSoupCream = ParsedRecipe(
            title = "Zupa pomidorowa krem",
            servings = "3",
            ingredients = "900 g Pomidory malinowe pieczone\n120 g Mozzarella\n30 g Pestki dyni\n20 ml Oliwa",
            instructions = "1. Upiecz pomidory.\n2. Zblenduj na krem i podaj z mozzarellą.",
            calories = 390, protein = 14, carbs = 38, fat = 19,
            source = "Jadlospis.pdf"
        )
        val overlapDuplicateOfApplePancakes = ParsedRecipe(
            title = "1. Placki z jabłkami",
            servings = "2",
            ingredients = "150 g Jabłka\n120 g Mąka orkiszowa\n150 g Jogurt naturalny\n2 szt Jajka\n5 g Cynamon",
            instructions = "1. Zetrzyj jabłka.\n2. Wymieszaj składniki i usmaż placki na złoty kolor.",
            calories = 420, protein = 18, carbs = 58, fat = 12,
            source = "Jadlospis.pdf"
        )

        assertFalse(PdfRecipeExtractor.areRecipesDuplicates(applePancakes, bananaPancakes))
        assertFalse(PdfRecipeExtractor.areRecipesDuplicates(tomatoSoupClassic, tomatoSoupCream))
        assertTrue(PdfRecipeExtractor.areRecipesDuplicates(applePancakes, overlapDuplicateOfApplePancakes))

        val deduplicated = PdfRecipeExtractor.deduplicateParsedRecipes(
            listOf(
                applePancakes,
                bananaPancakes,
                tomatoSoupClassic,
                tomatoSoupCream,
                overlapDuplicateOfApplePancakes
            )
        )
        assertEquals(4, deduplicated.size)
    }

    @Test
    fun malformedJsonAndRateLimitResume_workCorrectlyWithoutStoppingOrReprocessing() = runBlocking {
        val fakeChunks = listOf(
            PdfTextChunk(0, 4, "Chunk 1 text", 0, 100),
            PdfTextChunk(1, 4, "Chunk 2 empty intro text", 90, 200),
            PdfTextChunk(2, 4, "Chunk 3 malformed json text", 190, 300),
            PdfTextChunk(3, 4, "Chunk 4 final text", 290, 400)
        )

        val calledChunkIndices = mutableListOf<Int>()
        var simulateRateLimitOnChunk3 = true

        // First run: processes chunk 0 (1 recipe), chunk 1 (0 recipes - must NOT stop!), chunk 2 (malformed JSON -> repaired!), then hits HTTP 429 on chunk 3
        val firstRun = PdfRecipeExtractor.executePdfRecipeExtraction(
            documentTitle = "Test_Resume.pdf",
            chunks = fakeChunks,
            startChunkIndex = 0,
            chunkCall = { prompt ->
                when {
                    prompt.contains("Część 1/4") -> {
                        calledChunkIndices.add(0)
                        Result.success("""{"recipes":[{"title":"Placki z jabłkami","servings":"2","ingredients":[{"name":"Jabłka","quantity":"150","unit":"g"}],"instructions":["Wymieszaj i usmaż"],"calories":420,"protein":18,"carbs":58,"fat":12,"sourcePageOrChunk":"Część 1/4"}]}""")
                    }
                    prompt.contains("Część 2/4") -> {
                        calledChunkIndices.add(1)
                        Result.success("""{"recipes":[]}""")
                    }
                    prompt.contains("Część 3/4") -> {
                        calledChunkIndices.add(2)
                        // Return broken JSON that requires repairCall
                        Result.success("""BROKEN_JSON_RESPONSE {{{ not valid json""")
                    }
                    else -> {
                        calledChunkIndices.add(3)
                        if (simulateRateLimitOnChunk3) {
                            Result.failure(com.example.ai.GeminiRateLimitOrOverloadException("Limit zapytań do AI (HTTP 429)", 429))
                        } else {
                            Result.success("""{"recipes":[{"title":"Zupa pomidorowa krem","servings":"3","ingredients":[{"name":"Pomidory malinowe","quantity":"900","unit":"g"},{"name":"Mozzarella","quantity":"120","unit":"g"}],"instructions":["Upiecz i zblenduj"],"calories":390,"protein":14,"carbs":38,"fat":19,"sourcePageOrChunk":"Część 4/4"}]}""")
                        }
                    }
                }
            },
            repairCall = { _ ->
                Result.success("""{"recipes":[{"title":"Placki z bananem","servings":"2","ingredients":[{"name":"Banan","quantity":"160","unit":"g"},{"name":"Twaróg","quantity":"150","unit":"g"}],"instructions":["Rozgnieć i usmaż"],"calories":450,"protein":20,"carbs":62,"fat":14,"sourcePageOrChunk":"Część 3/4"}]}""")
            }
        )

        assertFalse(firstRun.completedAllChunks)
        assertTrue(firstRun.rateLimitedOrPaused)
        assertEquals(3, firstRun.nextChunkIndex)
        assertEquals(2, firstRun.deduplicatedRecipes.size)
        assertEquals(listOf(0, 1, 2, 3), calledChunkIndices)

        // Second run: Resume from unfinished chunk 3 without reprocessing chunks 0, 1, 2
        calledChunkIndices.clear()
        simulateRateLimitOnChunk3 = false

        val resumedRun = PdfRecipeExtractor.executePdfRecipeExtraction(
            documentTitle = "Test_Resume.pdf",
            chunks = fakeChunks,
            startChunkIndex = firstRun.nextChunkIndex,
            initialRawRecipes = firstRun.recipesBeforeDeduplication,
            initialPerChunkCounts = firstRun.perChunkCounts,
            chunkCall = { prompt ->
                if (prompt.contains("Część 4/4")) {
                    calledChunkIndices.add(3)
                    Result.success("""{"recipes":[{"title":"Zupa pomidorowa krem","servings":"3","ingredients":[{"name":"Pomidory malinowe","quantity":"900","unit":"g"},{"name":"Mozzarella","quantity":"120","unit":"g"}],"instructions":["Upiecz i zblenduj"],"calories":390,"protein":14,"carbs":38,"fat":19,"sourcePageOrChunk":"Część 4/4"}]}""")
                } else {
                    calledChunkIndices.add(-1)
                    Result.failure(IllegalStateException("Should not reprocess completed chunks"))
                }
            }
        )

        assertTrue(resumedRun.completedAllChunks)
        assertEquals(listOf(3), calledChunkIndices)
        assertEquals(3, resumedRun.deduplicatedRecipes.size)
    }

    @Test
    fun large30RecipePdf_fullEndToEndExtractionAndRoomSave() = runBlocking {
        // 1. Generate a real 15-page PDF with 30 distinct Polish recipes
        val pdfBytes = generateLargeRecipePdfBytes()
        val extractedText = PdfRecipeExtractor.extractFullTextFromPdf(ByteArrayInputStream(pdfBytes))
        assertTrue("Full PDF text should be extractable", PdfRecipeExtractor.hasExtractablePdfText(extractedText))
        assertTrue("All 15 pages should be extracted", extractedText.contains("=== STRONA 15 ==="))
        assertTrue("Recipe 30 should be in extracted text", extractedText.contains("PRZEPIS 30:"))

        // 2. Chunk with boundary-aware config (~4-5 chunks for ~30 recipes)
        val chunkConfig = PdfChunkConfig(
            targetChunkChars = 6_500,
            maxChunkChars = 8_500,
            minChunkChars = 4_500,
            overlapChars = 900
        )
        val chunks = PdfRecipeExtractor.createPdfChunks(extractedText, chunkConfig)
        println("REPORT_TEXT_LENGTH=${extractedText.length}")
        println("REPORT_TOTAL_CHUNKS=${chunks.size}")
        assertTrue("Should generate multiple chunks", chunks.size >= 3)

        // 3. Execute sequential extraction using robust mock parser
        val docTitle = "Jadlospis_30_Przepisow.pdf"
        val outcome = PdfRecipeExtractor.executePdfRecipeExtraction(
            documentTitle = docTitle,
            chunks = chunks,
            startChunkIndex = 0,
            chunkCall = { prompt ->
                val foundRecipes = mutableListOf<String>()
                for (id in 1..30) {
                    if (prompt.contains("PRZEPIS $id:")) {
                        val spec = thirtyRecipesCatalog[id - 1]
                        val ingredientsJson = spec.ingredients.joinToString(separator = ",") { ing ->
                            val parts = ing.split(" ")
                            val qty = parts.getOrNull(0) ?: ""
                            val unit = parts.getOrNull(1) ?: ""
                            val name = parts.drop(2).joinToString(" ")
                            """{"name": "${name.ifBlank { ing }}", "quantity": "$qty", "unit": "$unit"}"""
                        }
                        val instructionsJson = spec.steps.joinToString(separator = ",") { """"$it"""" }

                        foundRecipes.add("""
                            {
                                "title": "${spec.title}",
                                "servings": "${spec.servings}",
                                "ingredients": [$ingredientsJson],
                                "instructions": [$instructionsJson],
                                "calories": ${spec.calories},
                                "protein": ${spec.protein},
                                "carbs": ${spec.carbs},
                                "fat": ${spec.fat},
                                "sourcePageOrChunk": "Page X"
                            }
                        """.trimIndent())
                    }
                }
                val recipesJson = foundRecipes.joinToString(separator = ",")
                Result.success("""{"recipes":[$recipesJson]}""")
            },
            repairCall = { _ ->
                Result.success("""{"recipes":[]}""")
            }
        )

        for (i in chunks.indices) {
            println("REPORT_CHUNK_${i + 1}_RECIPES=${outcome.perChunkCounts[i] ?: 0}")
        }
        println("REPORT_TOTAL_BEFORE_DEDUP=${outcome.recipesBeforeDeduplication.size}")
        println("REPORT_TOTAL_AFTER_DEDUP=${outcome.deduplicatedRecipes.size}")

        assertTrue("All chunks should complete", outcome.completedAllChunks)
        assertTrue(
            "Expected nearly all or all 30 recipes (found ${outcome.deduplicatedRecipes.size})",
            outcome.deduplicatedRecipes.size >= 27
        )

        // Verify distinct similar-title recipes were NOT falsely merged
        val titlesLower = outcome.deduplicatedRecipes.map { it.title.lowercase() }
        assertTrue("Should contain Placki z jablkami", titlesLower.any { it.contains("placki") && it.contains("jab") })
        assertTrue("Should contain Placki z bananem", titlesLower.any { it.contains("placki") && it.contains("banan") })
        assertTrue("Should contain Zupa pomidorowa klasyczna", titlesLower.any { it.contains("pomidorowa") && it.contains("klasyczna") })
        assertTrue("Should contain Zupa pomidorowa krem", titlesLower.any { it.contains("pomidorowa") && it.contains("krem") })

        // 4. Save all extracted recipes to Room database and verify persistence
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = DietaRepository(db.dietaDao())

        for (r in outcome.deduplicatedRecipes) {
            repo.insertRecipe(
                RecipeEntity(
                    title = r.title,
                    description = "Porcje: ${r.servings} (Źródło: ${r.source})",
                    ingredients = r.ingredients,
                    instructions = r.instructions,
                    calories = r.calories,
                    protein = r.protein,
                    carbs = r.carbs,
                    fat = r.fat,
                    category = "Obiad",
                    source = r.source
                )
            )
        }

        val savedInRoom = repo.allRecipes.first()
        println("REPORT_FINAL_SAVED_COUNT=${savedInRoom.size}")
        assertEquals(outcome.deduplicatedRecipes.size, savedInRoom.size)
        assertTrue("All saved recipes must preserve PDF document name as source", savedInRoom.all { it.source == docTitle })
        assertTrue("All saved recipes must have non-blank ingredients and instructions", savedInRoom.all { it.ingredients.isNotBlank() && it.instructions.isNotBlank() })
        db.close()
    }
}
