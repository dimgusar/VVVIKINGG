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