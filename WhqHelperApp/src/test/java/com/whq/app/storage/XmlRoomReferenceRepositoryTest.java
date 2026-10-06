package com.whq.app.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import com.whq.app.i18n.Language;
import com.whq.app.model.WhiteDwarfRoomReferences.Reference;
import com.whq.app.RealContent;

/**
 * No-perdida de la migracion de WhiteDwarfRoomReferences.java a shared/data/xml/dungeon/room-references.xml.
 * Las huellas SHA-256 se calcularon sobre el texto original de la clase Java (19 salas, titulo y texto en
 * ingles y espanyol): cualquier caracter distinto o una traduccion ausente hace fallar el test.
 */
class XmlRoomReferenceRepositoryTest {

    private record Expected(long cardId, String name, String source,
            String titleEnSha256, String titleEsSha256, String textEnSha256, String textEsSha256) {
    }

    private static final List<Expected> ORIGINAL = List.of(
            new Expected(8, "FIRECHASM", "Adventure Book",
                    "0881f112620efe2d8204b35ba642dff86f890aedcf0c16b600f7b0db596e6df8",
                    "0881f112620efe2d8204b35ba642dff86f890aedcf0c16b600f7b0db596e6df8",
                    "b82cf13d3b16e580b385255a7a8d6eeefc9753e64fad0071ec5c93973eed79b4",
                    "8c923c1282b25265e032f5b8a7f1429d90c0c181cbde3e76a92a8cf57fa695e9"),
            new Expected(16, "TOMB CHAMBER", "Adventure Book",
                    "a87bb701f4a1e227aa0276c2fd9e78c5842d890afc36bb79bcb9509b01c29e0f",
                    "a87bb701f4a1e227aa0276c2fd9e78c5842d890afc36bb79bcb9509b01c29e0f",
                    "bc6127c77dd6486125da8ddb4c744775e7d22fcf556e54999ad230644d94b970",
                    "f9623904c98cb69372cb1d51252ec4395f09ee828754f76941e988ffb3e08ffe"),
            new Expected(18, "IDOL CHAMBER", "Adventure Book",
                    "612dc8c205bff68bcfc8780efed803727bd0d83280164c8d9fdc30042f9c1ede",
                    "612dc8c205bff68bcfc8780efed803727bd0d83280164c8d9fdc30042f9c1ede",
                    "f7aeeeb1ab6fa725a1521b0e29fcad26093dbec7ee06d5fe0986e98971553a8c",
                    "797a78f24cf038bc24545a99c1ec0f26a783e709cd9ae15379f081b40b9cf3c0"),
            new Expected(19, "FOUNTAIN OF LIGHT", "Adventure Book",
                    "2d4f459dcb8546adb777c3675df18af97c2511a1a15d5abd9215a93441c5cf4c",
                    "2d4f459dcb8546adb777c3675df18af97c2511a1a15d5abd9215a93441c5cf4c",
                    "5de54ddb92352230d6a283267dea7eaadd2913cec4fca314ec365071ce4413d8",
                    "1cad620097b14bff3ea83d873c6ca510708f3dd5fdcac5b3d8d2010b4e72c1d2"),
            new Expected(20, "FIGHTING PIT", "Adventure Book",
                    "f13b996f30b0a02859de40eee2315f8092ba2146401df245a665af5680178803",
                    "f13b996f30b0a02859de40eee2315f8092ba2146401df245a665af5680178803",
                    "2327cc3e2b6b6597b06b75ed635748537dcbfcace6cbc3a4510052bbee6cf006",
                    "be55894edf25e9181e781182674163e0bc10e344a6186d5aeaad010f73f6782d"),
            new Expected(21, "DREAD KING'S THRONE ROOM", "Catacombs of Terror",
                    "0969a07a9c655b14752da83248e4ef62194bf5680fc9b4297e9d9ead3a7d5901",
                    "41a8bae354c983f62a6ef2be07ff61c0069837d83ff842e3abef47df6af801fe",
                    "ae399d2cb73f2cdf3d22290671bdae0d625cac635f25148cb5df0ab2d5cf718b",
                    "a7ae893f555877fb960868ad0f03d7875ad588680e045ad2cab83646aead9146"),
            new Expected(26, "SHAMAN'S DEN", "Lair of the Orc Lord",
                    "5c75caa254c5eb4207ef1c6081a8d828fddeafd811e4dfb3df23fbc59a26c4c6",
                    "305cbf50109ca8fdf440be8cf0ee11bb5b29ad39d7e93fea41451de39e5a9220",
                    "ff0c3a72a855306eaa5471c95c4ec1508014fff329c7ca9b9f5136ec9295ef96",
                    "1e87422627d3e83d6a70b9517755bee757075bc043516f9f7679b4e403e009d2"),
            new Expected(27, "GORGUT'S LAIR", "Lair of the Orc Lord",
                    "2141606da19b81111c535cbb30bcd0d4544accf2b0631aeca3afff0df5e26a50",
                    "e12803480a75b3c09d0b37645cd1dcefd83e6d061704b0b8ce1119fe8eab18e2",
                    "064ef0c0f409456a7a84ab87aa52c56687d6e5fb98a431024d91e8ed76d9a60b",
                    "9e9c10ea440dc07f56624d27a644251ae85fcc71396270135b307824f9fdf349"),
            new Expected(28, "DEAD END", "White Dwarf 192",
                    "29611d2b3232eb89b8c0c338eaa1f877e98d95d3d173719a988b969035521a6b",
                    "29611d2b3232eb89b8c0c338eaa1f877e98d95d3d173719a988b969035521a6b",
                    "41a5bb6bfb3ed37cc8e8c813a808d6a4b673e31e7206d545092adc9012bad84e",
                    "b2164cd0244bf3d5bfc387639fbada23bc716fafe2b7f5af83c9ffd4d2385867"),
            new Expected(29, "THE GAOL", "White Dwarf 185",
                    "243931c16be09766209db15b99e29d03a2739460cbbfa997c256e86ad6d03571",
                    "2ee59568ab75fd034f9714515c65a976f80d88a5af593a4123b669c697f52d4b",
                    "6b44f1908bf0188ebd5db1faf3ade7001d3e275e69e4ea8492503a0efb0217d2",
                    "7de8cf342ad7145c2cc0e8de06b394945d60b45649f1616eccab3e3185170b39"),
            new Expected(30, "INTO THE DARK", "White Dwarf 192",
                    "1d2491fb8088a1e326b7c7ea1b5ef40780d3abc42ba19f9aee429063b0b7e825",
                    "1d2491fb8088a1e326b7c7ea1b5ef40780d3abc42ba19f9aee429063b0b7e825",
                    "071d50ac024783fa02087bf31973a15e119d91c59f915e62c245399999d00acc",
                    "835bd34197190c8fac1805fea8cb90625355ddd6d38966f465a0daf172ecd5f1"),
            new Expected(31, "SEWER", "White Dwarf 204",
                    "ff98ab10a5a130c0af17453d477910e88c43e3f98c878eb748a4dfccbf3635de",
                    "ff98ab10a5a130c0af17453d477910e88c43e3f98c878eb748a4dfccbf3635de",
                    "9c35e38b58740cf8a9f24da9de085139ff460724d94dccf4ff262acf1478aa6b",
                    "5b2207723abbffa7f073d2432d55b9a1770476d5901b85b2988e856f51b9f3f3"),
            new Expected(32, "QUIRRIK'S LABORATORY", "White Dwarf 195",
                    "0bd1dea0310c3e39f07035b03fbc91cf47d8d6d4a9cad37462f957fcb0652b9a",
                    "728eeba8a91a19bbc79a61998060706e0e9852438778e5e163cb27174a442e6c",
                    "493eccfd9411d73a9600003a00b003d926bb299942a2be883cb2ec127ce99329",
                    "32bc2d57dd653dd811fdded3f6d203a742b270f7ff561ed0ce30e76ea933fa08"),
            new Expected(33, "DWARVEN FORGE", "UAB 3.1",
                    "723583f391348476b35e391fac85ed1269f16aca79b785bcb0fe47e431143fc1",
                    "95fa580f45ce9bdb3543d73547051cd7ce077576ae400e806047503577ca7663",
                    "07be211b3656639dee8b3399d19301d0d87b2db3e8f42ca1ba49cb573a86d157",
                    "1f08d927ed3a527ce3ed0528801ddac1af7a1da2454db2198b005c93a84ee636"),
            new Expected(34, "CROSSROADS OF DOOM", "Pits & Traps",
                    "850087d64d46cfb63ae99687c159d7bb9e82c154bb7dfe7845028a43c5884804",
                    "850087d64d46cfb63ae99687c159d7bb9e82c154bb7dfe7845028a43c5884804",
                    "30e99334c4db76c5b45cac1e7bd5ce1d62d5f1e1d7b044ba60b66ef93d5b63a3",
                    "2b9a6fe9da6c99dabd1be436c8c2e502d4936f2a0f5ae0b0a22eb697e4736dfa"),
            new Expected(35, "BRIDGE OF DESPAIR", "Pits & Traps",
                    "beab7a60d67589b2183e9cfac2822a22464375091c3a1413e3630f61d858c53d",
                    "beab7a60d67589b2183e9cfac2822a22464375091c3a1413e3630f61d858c53d",
                    "c892215e471c5c3a6a755d5bb01070684a5d3d70164257629193fcc26947b7d2",
                    "b689f486cc60f6a60f24f5fa94500a0cd0eeffda49c8f44b16cbdfcbf7d8391e"),
            new Expected(54, "WATER CAVE", "Hall of the Hag Queen",
                    "69669c8c2761290c297301f2480fff18900e931e004ae48d3d27230350a61719",
                    "69669c8c2761290c297301f2480fff18900e931e004ae48d3d27230350a61719",
                    "f568063f0156a769236f08d2dc93b462f7e04c9905fe8c899edff07377735fc5",
                    "27475c3e6403d0f7a45439013a71300c2388965f7764bddf7e34b49e601be9f7"),
            new Expected(55, "TEMPLE OF KHAINE", "Hall of the Hag Queen",
                    "65c3a9d494656878fa4ea711ca9bc0545a5c6770c4600ad59244b79396f1557b",
                    "65c3a9d494656878fa4ea711ca9bc0545a5c6770c4600ad59244b79396f1557b",
                    "eb55574eddf00ba4aee7861b409f71644fd9d7009e4825fa4b91b628179c2c63",
                    "5e378e45fba2c3a28b2587ad82ca5c9b541f30bb1fa0a5a64841a086c2b3199b"),
            new Expected(56, "BOTTOMLESS PIT", "Hall of the Hag Queen",
                    "4a4d1e815e802699407b15706275ecb99bc71a52d93567f1ea68636c96a94cf4",
                    "4a4d1e815e802699407b15706275ecb99bc71a52d93567f1ea68636c96a94cf4",
                    "fbd8bce2ca60044c4825d14861700cf3d52d07a3b72538e1f013207d8888ac61",
                    "9ef07a2574f2c255c9545f368ac20050b9e1d752680aac92618fc1a195fbf47d")
    );

    private final XmlRoomReferenceRepository repository = new XmlRoomReferenceRepository(Path.of(""));

    @BeforeEach
    void requireRealContent() {
        RealContent.assumeAvailable();
    }

    @Test
    void sharedContentHasExactlyTheNineteenOriginalRooms() {
        assertEquals(
                ORIGINAL.stream().map(Expected::cardId).collect(Collectors.toSet()),
                repository.findAll().keySet());
    }

    @Test
    void everyRoomKeepsSourceTitleAndTextInBothLanguages() throws Exception {
        for (Expected expected : ORIGINAL) {
            Optional<Reference> found = repository.find(expected.cardId());
            assertTrue(found.isPresent(), "Falta la referencia de " + expected.name());
            Reference reference = found.get();
            assertEquals(expected.source(), reference.source(), expected.name());
            assertEquals(expected.titleEnSha256(), sha256(reference.title(Language.EN)), expected.name() + " title EN");
            assertEquals(expected.titleEsSha256(), sha256(reference.title(Language.ES)), expected.name() + " title ES");
            assertEquals(expected.textEnSha256(), sha256(reference.text(Language.EN)), expected.name() + " text EN");
            assertEquals(expected.textEsSha256(), sha256(reference.text(Language.ES)), expected.name() + " text ES");
        }
    }

    @Test
    void lookupIsByCardId() {
        Map<Long, Reference> all = repository.findAll();
        assertTrue(all.containsKey(56L));
        assertTrue(repository.find(999_999L).isEmpty());
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
