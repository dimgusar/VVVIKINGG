import java.time.LocalDate;
import java.time.Month;
import java.util.*;

public class Game {

    // драккары, викинги дла найма, деревни
    final ArrayList<Drakkar> allDrakkars = new ArrayList<>();
    final ArrayList<Village> allVillages = new ArrayList<>();
    final ArrayList<Viking> allVikings = new ArrayList<>();
    // макс широта и долгота. Для удобства в км.
    final int maxLat;
    final int maxLng;
    // имена и короткие имена деревень
    final static TreeMap<VillageType, String> villageTypeNames = new TreeMap<>();
    final static TreeMap<VillageType, String> villageShortNames = new TreeMap<>();
    // перевод типов добычи
    final static TreeMap<LootType, String> lootTypeNames = new TreeMap<>();
    // маршрут набега
    final ArrayList<Village> conquestPath = new ArrayList<>();
    // число серебра в сокровищнице
    int silverPieces = 0;
    // текущая дата
    LocalDate currentDate = LocalDate.of(1000, Month.MAY, 6);
    // список возможных личных имён
    final List<String> firstNames = List.of("Eric", "Harald", "Dyre", "Denholm", "Knute", "Niels", "Egil", "Bjarn", "Arvid", "Ingvar", "Snorre", "Sven", "Steinar");
    // список родов
    final List<String> families = List.of("Arenander", "Brandvold", "Elfving", "Gunnarson", "Holmsten", "Kjellson", "Lundevall", "Magnussen", "Nydahl", "Olasson", "Renberg", "Thorstad", "Widding", "Ylandeer");
    // цена разных видов добычи
    final TreeMap<LootType, Integer> priceSilverPerLoot=new TreeMap<>();
    // цена раба
    final int priceSilerPerSlave = 3;
    // сколько мер еды берет каждый викинг в поход
    static final int START_FOOD_PER_VIKING = 2;
    // число доступных к найму в начале игры
    static final int START_VIKING_COUNT = 10;
    // число деревень
    static final int VILLAGES_COUNT = 10;
    // чсисло драккаров
    static final int DRAKKAR_COUNT = 10;
    // цена добавления нового викинга к списку доступных для найма
    static final int PRICE_PER_NEW_VIKING = 100;
    // хранилище кодов использованных комбинаций имени и рода, чтобы не было повторов
    TreeSet<Integer> usedCombos = new TreeSet<>();
    Game(int mxLng, int mxLt){
        maxLat = mxLt;
        maxLng = mxLng;
        Random rnd = new Random();
        // генерируем драккары
        for(int k = 0; k < DRAKKAR_COUNT; ++k){
            int ps = rnd.nextInt(3,8);
            int hs = ps*2 + rnd.nextInt(2);
            int gs = ps*30;
            int ms = 15 + rnd.nextInt(-5, 5);

            allDrakkars.add(new Drakkar(ps, hs, gs, ms));
        }

        // устанавливаем цены
        priceSilverPerLoot.put(LootType.FOOD,1);
        priceSilverPerLoot.put(LootType.FISH,2);
        priceSilverPerLoot.put(LootType.LEATHER,3);
        priceSilverPerLoot.put(LootType.TNP,4);
        priceSilverPerLoot.put(LootType.JEWELRY,5);


        // генерируем викингов
        for(int k = 0; k < START_VIKING_COUNT; ++k){
            generateNewViking();
        }

        // добавляем переводы типов деревень
        villageTypeNames.put(VillageType.MONASTERY, "Монастырь");
        villageTypeNames.put(VillageType.TRADE_POST, "Торговый пост");
        villageTypeNames.put(VillageType.CRAFTER_VILLAGE, "Деревня ремесленников");
        villageTypeNames.put(VillageType.FISHER_VILLAGE, "Деревня рыбаков");
        villageTypeNames.put(VillageType.PEASANT_VILLAGE, "Деревня земледельцев");
        villageTypeNames.put(VillageType.RANCH_VILLAGE, "Деревня скотоводов");


        // добавляем краткие переводы
        villageShortNames.put(VillageType.MONASTERY, "Мо");
        villageShortNames.put(VillageType.TRADE_POST, "То");
        villageShortNames.put(VillageType.CRAFTER_VILLAGE, "Ре");
        villageShortNames.put(VillageType.FISHER_VILLAGE, "Ры");
        villageShortNames.put(VillageType.PEASANT_VILLAGE, "Зе");
        villageShortNames.put(VillageType.RANCH_VILLAGE, "Ск");

        // добавляекм переводы типов добычи
        lootTypeNames.put(LootType.FOOD, "Еда");
        lootTypeNames.put(LootType.TNP, "ТНП");
        lootTypeNames.put(LootType.FISH, "Рыба");
        lootTypeNames.put(LootType.LEATHER, "Кожа");
        lootTypeNames.put(LootType.JEWELRY, "Драгоценности");

        // генерируем деревни
        while(allVillages.size() < VILLAGES_COUNT){
            // случайные координаты
            int rndLat = rnd.nextInt(10, maxLat-40);
            int rndLng = rnd.nextInt(10, maxLng-40);

            // но не слишком близко
            boolean tooClose = false;
            for(int k = 0; k < allVillages.size(); ++k){
                int deltaLat = rndLat-allVillages.get(k).lattitude;
                int deltaLng = rndLng-allVillages.get(k).longitude;
                if(Math.pow(deltaLng,2)+Math.pow(deltaLat,2) < 10000){
                    tooClose = true;
                    break;
                }
            }
            if(tooClose){
                continue;
            }

            // random village type, level

            // случайный тип из enum
            int villageTypeIndex = rnd.nextInt(VillageType.values().length);
            int villageLevel = (int)Math.round(rnd.nextDouble()*rnd.nextDouble()*10) + 1;
            if(villageLevel > 10){
                villageLevel = 10;
            }

            // добавляем деревню
            allVillages.add(new Village(VillageType.values()[villageTypeIndex], villageLevel, rndLng, rndLat));

        }

    }

    // генерация нового викинга ( в начале игры и по запросу)
    public void generateNewViking(){
        Random rnd = new Random();
        // выбираем случайную неиспользованную комбинацию
        while(true){
            int k_fn = rnd.nextInt(firstNames.size());
            int k_ln = rnd.nextInt(families.size());
            int combo = k_fn* families.size()+k_ln;
            if(usedCombos.contains(combo)){
                continue;
            }
            usedCombos.add(combo);
            allVikings.add(new Viking(firstNames.get(k_fn), families.get(k_ln), rnd.nextInt(35,55)));
            return;
        }
    }

    // вероятность успеха
    // может быть > 100% - показатель надёжности
    public static double probabilityFightSuccess(int teamSize, int targetLevel){
        double p = 0.1;
        p /= targetLevel;
        p *= teamSize;
        return  p;
    }
}
