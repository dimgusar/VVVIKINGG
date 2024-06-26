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
