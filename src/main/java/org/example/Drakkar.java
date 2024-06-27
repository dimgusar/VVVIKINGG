import java.util.TreeMap;
public class Drakkar {

    // пары скамей для гребцов
    final int N_PAIRS;
    // емкость грузового трюма
    final int SPACE_FOR_GOODS;
    // место для людей
    final int SPACE_FOR_MEN;
    // сколько сейчас викингов
    int currentVikings;
    // сколько рабов
    int currentSlaves;
    // максимальная скорость (при полном занятии всех пар скамей)
    int MAX_SPEED;

    // инфа по загруженной добыче

    TreeMap<LootType,Integer> lootInfo = new TreeMap<>();
    Drakkar(int np, int hs, int sp, int ms){
        N_PAIRS = np;
        SPACE_FOR_MEN = hs;
        SPACE_FOR_GOODS = sp;
        MAX_SPEED = ms;
    }
    // вычислить скорость при данном экипаже (учитываются и рабы)
    double computeSpeed(){
        int paddlersPairs = (currentVikings+currentSlaves)/2;
        if(paddlersPairs > N_PAIRS){
            paddlersPairs = N_PAIRS;
        }
        return MAX_SPEED*1.0*paddlersPairs/N_PAIRS;
    }
    // добавить добычу
    // еда и рыба хранятся как десятые доли для удобства
    void addLoot(LootType type, int amount){

        if(type==LootType.FOOD || type==LootType.FISH){
            amount *= 10;
        }
        // если есть, добавить
        if(lootInfo.containsKey(type)){
            lootInfo.put(type, lootInfo.get(type)+amount);
        }
        else{
            // иначе просто положить
            lootInfo.put(type, amount);
        }
    }

    // очистка драккара (возврат в гараж)
    void clear(){
        currentSlaves = 0;
        currentVikings = 0;
        for(var x : LootType.values()){
            lootInfo.put(x, 0);
        }
    }

    // описание текущего состояния драккара
    @Override
    public String toString(){
        return String.format("%2d пар | %2d | %3d | %2d км/ч", N_PAIRS, SPACE_FOR_MEN, SPACE_FOR_GOODS, MAX_SPEED);
    }
}