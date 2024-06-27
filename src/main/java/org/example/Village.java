public class Village {

    final int level;
    boolean destroyed = false;
    int slaves;
    final VillageType theType;
    final int longitude;
    final int lattitude;
    TreeMap<LootType, Integer> all_loot;
    Village(VillageType vt, int lvl, int lng, int lat){
        theType = vt;
        level = lvl;
        Random rnd = new Random();
        longitude = lng;
        lattitude = lat;
        slaves = lvl*2;
        all_loot = new TreeMap<>();
        if(theType==VillageType.MONASTERY){
            all_loot.put(LootType.JEWELRY, level*10);
        }
        else if(theType==VillageType.FISHER_VILLAGE){
            all_loot.put(LootType.FISH, level*10);
        }
        else if(theType==VillageType.PEASANT_VILLAGE){
            all_loot.put(LootType.FOOD, level*10);
        }
        else if(theType==VillageType.RANCH_VILLAGE){
            all_loot.put(LootType.LEATHER, level*10);
        }
        else if(theType==VillageType.CRAFTER_VILLAGE){
            all_loot.put(LootType.TNP, level*10);
        }
        else{
            for(var x : LootType.values()){
                all_loot.put(x, 2*level);
            }
        }
    }
    // перевод инфы про деревню (коротая версия)
    public String shortInfo(){
        return String.format("%s%d", Game.villageShortNames.get(theType), level);
    }

    // координаты деревни в логических единицах (зависят от размера сетки)
    public int getCellX(int cellSize){
        return (int)Math.round(longitude*1.0/cellSize);
    }
    public int getCellY(int cellSize){
        return (int)Math.round(lattitude*1.0/cellSize);
    }
    // подробное описание деревни
    @Override
    public String toString(){

        String info = String.format("%s | ", Game.villageTypeNames.get(theType));
        info += String.format("Уровень: %d | Широта: %d | Долгота: %d", level,lattitude,longitude);

        return info;
    }

    // информация про доступную добычу
    public String getLootInfo(){

        String info = "";
        int typesCnt = 0;
        for(var x : LootType.values()){
            if (all_loot.containsKey(x)) {
                typesCnt += 1;
            }
        }
        int j = 0;
        for(var x : LootType.values()){
            if (all_loot.containsKey(x)) {
                info += String.format("%s: %d", Game.lootTypeNames.get(x), all_loot.get(x));
                if(j < typesCnt-1){
                    info += ", ";
                }
                j++;
            }
        }
        return info;
    }
}
