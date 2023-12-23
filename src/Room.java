import java.util.List;
import java.util.ArrayList;

public class Room{
    private String name;
    private List<String> users;

    Room(String name){
        this.name = name;
        this.users = new ArrayList<>();
        
    }

    public void addUser(String user){
        this.users.add(user);
    }

    public void removeUser(String user){
        this.users.remove(user);
    }

    public List<String> getUsers(){
        return this.users;
    }

    public String getName(){
        return this.name;
    }

}
