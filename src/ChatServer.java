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

  static private Map<Integer,String> nicknames = new HashMap<>();

  static private Map<String,List<String>> room = new HashMap<>();

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
    // Read the message to the buffer
    buffer.clear();
    sc.read( buffer );
    buffer.flip();

    // If no data, close the connection
    if (buffer.limit()==0) {
      return false;
    }
    
    String msg = decoder.decode(buffer).toString(); 
    System.out.print(msg);

    String[] command = msg.split(" ",msg.length());
        
    Integer clientPort = sc.socket().getPort();

    switch(command[0]){
        case "/nick": 
            String name = command[1];
            String resNick = disponivel(name,clientPort);
            System.out.println(resNick);
            break;
        case "/join":
            String roomName = command[1];
            String resJoin = createRoom(roomName,clientPort);
            System.out.println(resJoin);
            break;
        case "/leave":
            break;
        case "/bye":
            break;
    }

    for (Map.Entry<String, List<String>> entry : room.entrySet()) {
        String key = entry.getKey();
        List<String> value = entry.getValue();

        System.out.println ("Key: " + key + " Value: " + value);
}
    // Decode and print the message to stdout
    for(SocketChannel channel : channelList){ // alinea d
        channel.write(buffer); // alinea c
        buffer.flip();
    }
    return true;
  }

  static public String disponivel(String name,Integer port){
      if(nicknames.containsValue(name)){
          return "Error";
      }
      nicknames.put(port,name);
      return "Ok";
  }

  static public String createRoom(String name,Integer port){
      if(room.containsKey(name)){
          room.get(name).add(nicknames.get(port));
          return "OK";
      }
      room.put(name,new ArrayList<String>());
      room.get(name).add(nicknames.get(port));
      return "OK";
  }
}




