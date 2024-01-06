import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

public class ChatServer
{
  // A pre-allocated buffer for the received data
  static private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );

  // Decoder for incoming text -- assume UTF-8
  static private final Charset charset = Charset.forName("UTF8");
  static private final CharsetDecoder decoder = charset.newDecoder();

  static private List<SocketChannel> channelList = new ArrayList<>();

  static private Map<Integer,String> state = new HashMap<>();

  static private Map<Integer,String> nicknames = new HashMap<>();

  static private List<Room> rooms = new ArrayList<>();

  static private List<String> commands = Arrays.asList("/nick", "/join", "/leave", "/bye", "/priv");

  static private Map<Integer,String> resString = new HashMap<>();

  static public void main( String args[] ) throws Exception {
    // Parse port from command line
    int port = Integer.parseInt( args[0] );
    
    try {
      // Instead of creating a ServerSocket, create a ServerSocketChannel
      ServerSocketChannel ssc = ServerSocketChannel.open();

      // Set it to non-blocking, so we can use select
      ssc.configureBlocking( false );

      // Get the Socket connected to this channel, and bind it to the
      // listening port
      ServerSocket ss = ssc.socket();
      InetSocketAddress isa = new InetSocketAddress( port );
      ss.bind( isa );

      // Create a new Selector for selecting
      Selector selector = Selector.open();

      // Register the ServerSocketChannel, so we can listen for incoming
      // connections
      ssc.register( selector, SelectionKey.OP_ACCEPT );
      System.out.println( "Listening on port "+port );

      while (true) {
        // See if we've had any activity -- either an incoming connection,
        // or incoming data on an existing connection
        int num = selector.select();

        // If we don't have any activity, loop around and wait again
        if (num == 0) {
          continue;
        }

        // Get the keys corresponding to the activity that has been
        // detected, and process them one by one
        Set<SelectionKey> keys = selector.selectedKeys();
        Iterator<SelectionKey> it = keys.iterator();
        while (it.hasNext()) {
          // Get a key representing one of bits of I/O activity
          SelectionKey key = it.next();

          // What kind of activity is it?
          if (key.isAcceptable()) {

            // It's an incoming connection.  Register this socket with
            // the Selector so we can listen for input on it
            Socket s = ss.accept();
            System.out.println( "Got connection from "+s );

            nicknames.put(s.getPort(),"");
            state.put(s.getPort(),"init");

            // Make sure to make it non-blocking, so we can use a selector
            // on it.
            SocketChannel sc = s.getChannel();
            sc.configureBlocking( false );

            // Register it with the selector, for reading
            sc.register( selector, SelectionKey.OP_READ );
            channelList.add(sc);


          } else if (key.isReadable()) {

            SocketChannel sc = null;

            try {
              
              // It's incoming data on a connection -- process it
              sc = (SocketChannel)key.channel();
              boolean ok = processInput( sc);

              // If the connection is dead, remove it from the selector
              // and close it
              if (!ok) {
                key.cancel();

                channelList.remove(sc);
                Socket s = null;
                try {
                  s = sc.socket();
                  System.out.println( "Closing connection to "+s );
                  s.close();
                } catch( IOException ie ) {
                  System.err.println( "Error closing socket "+s+": "+ie );
                }
              }

            } catch( IOException ie ) {

              // On exception, remove this channel from the selector
              key.cancel();

              try {
                sc.close();
              } catch( IOException ie2 ) { System.out.println( ie2 ); }

              System.out.println( "Closed "+sc );
            }
          }
        }

        // We remove the selected keys, because we've dealt with them.
        keys.clear();
      }
    } catch( IOException ie ) {
      System.err.println( ie );
    }
  }


  // Just read the message from the socket and send it to stdout
  static private boolean processInput( SocketChannel sc) throws IOException {
    Integer clientPort = sc.socket().getPort();

    // Read the message to the buffer
    buffer.clear();
    sc.read( buffer);
    buffer.flip();


    // If no data, close the connection
    if (buffer.limit()==0) {
      if(state.get(clientPort) == "inside"){
          String str = "LEFT " + nicknames.get(clientPort);
          messageOthers(nicknames.get(clientPort),str,sc);
          removeFromRoom(clientPort);
      }
      nicknames.remove(clientPort);
      return false;
    }

    
    String msg = decoder.decode(buffer).toString(); 
    if(resString.containsKey(clientPort)){
        resString.put(clientPort,resString.get(clientPort) + msg);
    }else{
        resString.put(clientPort,msg);
    }

    System.out.print(msg);

    if(msg.contains("\n")){
        String[] msgs = resString.get(clientPort).split("\n");
        resString.put(clientPort,"");
        for(String str : msgs){
            Boolean tmp = protocol(str,sc,clientPort);
            if(!tmp) return false;
        }
    }
    return true;

  }

   static public boolean protocol(String msg,SocketChannel sc,Integer clientPort) throws IOException{
    String[] command = msg.split(" ",msg.length());
        

    String str;
    switch(command[0]){
        case "/nick": 
            if(state.get(clientPort) == "init"){
                String nick = command[1];
                String resNick = disponivel(nick,clientPort);
                if(resNick == "Ok"){
                    messageUser("OK\n",sc);
                    state.put(clientPort,"outside");
                }else if(resNick == "Error"){
                    messageUser("ERROR\n",sc);
                    state.put(clientPort,"init");
                }
            }
            else if(state.get(clientPort) == "outside"){
                String nick = command[1];
                String resNick = disponivel(nick,clientPort);
                if(resNick == "Ok"){
                    messageUser("OK\n",sc);
                    state.put(clientPort,"outside");
                }else if(resNick == "Error"){
                    messageUser("ERROR\n",sc);
                    state.put(clientPort,"outside");
                }
            }   
            else if(state.get(clientPort) == "inside"){
                String nick = command[1];
                String oldNick = nicknames.get(clientPort);
                String resNick = disponivel(nick,clientPort);
                if(resNick == "Ok"){
                    changeNick(oldNick,nick);
                    String oldNick2 = oldNick.replace("\n", "");
                    messageUser("OK\n",sc);
                    messageOthers(nicknames.get(clientPort),"NEWNICK " + oldNick2+ " " + nick+"\n",sc);
                    state.put(clientPort,"inside");
                }else if(resNick == "Error"){
                    messageUser("ERROR\n",sc);
                    state.put(clientPort,"inside");
                }
            }else{
                messageUser("ERROR\n",sc);
            }
            break;
        case "/join":
            if(state.get(clientPort) == "outside"){
                String roomName = command[1];
                String resJoin = joinRoom(roomName,clientPort,"outside",sc);
                messageUser("OK\n",sc);
                if(resJoin == "OK"){
                    state.put(clientPort,"inside");
                }
            }
            else if(state.get(clientPort) == "inside"){
                String roomName = command[1];
                String resJoin = joinRoom(roomName,clientPort,"inside",sc);
                messageUser("OK\n",sc);
                if(resJoin == "OK"){
                    state.put(clientPort,"inside");
                }
            }else{
                messageUser("ERROR\n",sc);
            }
            for (Room room : rooms){
                System.out.println(room.getName());
                for (String user : room.getUsers()){
                    System.out.println(user);
                }
            }
            break;
        case "/leave":
            if(state.get(clientPort) == "inside"){
                messageUser("OK\n",sc);
                str = "LEFT " + nicknames.get(clientPort)+"\n";
                messageOthers(nicknames.get(clientPort),str,sc);
                state.put(clientPort,"outside");
                removeFromRoom(clientPort);
            }else{
                messageUser("ERROR\n",sc);
            }
            break;
        case "/bye":
            if(state.get(clientPort) == "inside"){
                str = "LEFT " + nicknames.get(clientPort);
                messageOthers(nicknames.get(clientPort),str,sc);
                removeFromRoom(clientPort);
            }
            messageUser("BYE\n",sc);
            nicknames.remove(clientPort);
            return false;
        case "/priv":
            if(state.get(clientPort) != "init"){
                String nick = command[1];
                if(nicknames.containsValue(nick)){
                    messageUser("OK\n",sc);
                    SocketChannel chn = null;
                    String nickname = nicknames.get(clientPort).replace("\n", "");
                    String txt = "" ;
                    int i=2;
                    while (command.length > i){
                        txt += command[i] + " ";
                        i++;
                    }
                    msg = "PRIVATE " + nickname + " " + txt+ "\n";
                    
                    buffer.clear();
                    buffer.put(msg.getBytes());
                    buffer.flip();
                    for(SocketChannel channel : channelList){
                        if(nicknames.get(channel.socket().getPort()).equals(nick)){
                            chn = channel;
                            chn.write(buffer);
                            buffer.flip();
                            break;
                        }
                    }

                }else{
                    messageUser("ERROR\n",sc);
                    break;
                }
            }else{
                messageUser("ERROR\n",sc);
            }
            break;
        default:
            if(state.get(clientPort) == "inside"){
                if(msg.charAt(0) == '/'){
                    if(!commands.contains(command[0]))
                        msg = msg.substring(1,msg.length());
                }
                String nickname = nicknames.get(clientPort).replace("\n", "");
                str = "MESSAGE " + nickname + " " + msg+"\n";
                messageUser(str,sc);
                messageOthers(nicknames.get(clientPort),str,sc);
            }else{
                messageUser("ERROR\n",sc);
            }
            break;
    }
    /*
    for (Map.Entry<String, List<String>> entry : room.entrySet()) {
        String key = entry.getKey();
        List<String> value = entry.getValue();

        System.out.println ("Key: " + key + " Value: " + value);
    }
    \Decode and print the message to stdout
    for(SocketChannel channel : channelList){ // alinea d
        if (channel.socket().getPort() == clientPort) continue;
        for (String roomName : room.keySet()){
            if(room.get(roomName).contains(nicknames.get(clientPort))){
                channel.write(buffer);
                buffer.flip();
            }
        } 
    }*/
    return true;
  }

  static public String disponivel(String name,Integer port){
      if(nicknames.containsValue(name)){
          return "Error";
      }
      nicknames.put(port,name);
      return "Ok";
  }

  static public String joinRoom(String name,Integer port,String state,SocketChannel sc){
      String nickname = nicknames.get(port);
      if(state == "inside"){
          messageOthers(nicknames.get(port),"LEFT " + nickname+"\n",sc);
          removeFromRoom(port);
      }
      for (Room room : rooms){
          if(room.getName().equals(name)){
              room.addUser(nicknames.get(port));
              messageOthers(nicknames.get(port),"JOINED " + nickname+"\n",sc);
              return "OK";
          }
      }
      Room room = new Room(name);
      room.addUser(nicknames.get(port));
      messageOthers(nicknames.get(port),"JOINED " + nickname+"\n",sc);
      rooms.add(room);
      return "OK";
  }

  static public void removeFromRoom(Integer clientPort){
    for (Room room : rooms){
        for (String user : room.getUsers()){
            if(user == nicknames.get(clientPort)){
                room.removeUser(nicknames.get(clientPort));
                break;
            }
        }
    }
  }

 static public void messageUser(String msg,SocketChannel sc){
      try{
        buffer.clear();
        buffer.put(msg.getBytes());
        buffer.flip();
        sc.write(buffer);
      }catch(IOException ie){
        System.out.println(ie);
      }
  }

  static public void changeNick(String oldNick,String newNick){
      for (Room room : rooms){
          for (String user : room.getUsers()){
              if(user.equals(oldNick)){
                  room.removeUser(oldNick);
                  room.addUser(newNick);
                  break;
              }
          }
      }
  }
  static public void messageOthers(String name,String msg,SocketChannel sc){
      try{
        buffer.clear();
        buffer.put(msg.getBytes());
        buffer.flip();
        for(SocketChannel channel : channelList){
            if (channel.socket().getPort() == sc.socket().getPort()) continue;
            for (Room room : rooms){
                if(room.getUsers().contains(nicknames.get(channel.socket().getPort()))
                        && room.getUsers().contains(name)){
                    channel.write(buffer);
                    buffer.flip();
                }

            }
        }
      }catch(IOException ie){
        System.out.println(ie);
      }
  }

}




