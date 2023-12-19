import java.util.ArrayList;
import java.util.List;

class Room{
    private String name;
    private List<String> users;

    Room(String name){
        this.name = name;
        this.users = new ArrayList<>();
    }
}
