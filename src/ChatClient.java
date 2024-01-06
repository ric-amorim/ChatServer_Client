import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.net.*;
import java.util.List;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;


public class ChatClient {

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();
    // --- Fim das variáveis relacionadas coma interface gráfica

    // Se for necessário adicionar variáveis ao objecto ChatClient, devem
    // ser colocadas aqui
    static private final Charset charset = Charset.forName("UTF8");
    static private final CharsetDecoder decoder = charset.newDecoder();
    static private ByteBuffer buffer = ByteBuffer.allocate(16384);
    static private List<String> commands = Arrays.asList("/nick", "/join", "/leave", "/bye", "/priv");

    private String server;
    private int port;
    private SocketChannel s;


    public String lastMessage;
    
    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String message) {
        chatArea.append(message);
    }

    
    // Construtor
    public ChatClient(String server, int port) throws IOException {

        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                } finally {
                    chatBox.setText("");
                }
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                chatBox.requestFocusInWindow();
            }
        });
        // --- Fim da inicialização da interface gráfica

        // Se for necessário adicionar código de inicialização ao
        // construtor, deve ser colocado aqui

        /*
         *
         *    Non-blocking mode is useful in 
         *    conjunction with selector-based multiplexing (ChatServer).
         *
         */

        s = SocketChannel.open(new InetSocketAddress(server, port));
        s.configureBlocking(false);


        this.server = server;
        this.port = port;

    }


    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
        message = message + "\n";
        if(message.charAt(0) == '/'){
            String[] command = message.split(" ");
            if(!commands.contains(command[0]))
                message = "/" + message;
        }
        lastMessage = message;
        s.write(charset.encode(message));

          

    }

    public void processInput(String input) {
        String[] response = input.split(" ");

        switch(response[0].trim()){
            case "ERROR":
                String last = lastMessage.split(" ")[0];
                switch(last){
                    case "/nick":
                        printMessage("Error: Nickname already in use.\n");
                        break;
                    case "/join":
                        printMessage("Error: You don't have a nickname yet\n");
                        break;
                    case "/leave":
                        printMessage("Error: You are not in a room.\n");
                        break;
                    case "/priv":
                        printMessage("Error: User not found.\n");
                        break;
                    default:
                        printMessage("Error: Invalid command.\n");
                        break;
                }
                break;
            case "OK":
                String last2 = lastMessage.split(" ")[0];
                switch(last2){
                    case "/nick":
                        printMessage("Nickname changed.\n");
                        break;
                    case "/join":
                        printMessage("Joined room.\n");
                        break;
                    case "/leave":
                        printMessage("Left room.\n");
                        break;
                    case "/priv":
                        printMessage("Private message sent.\n");
                        break;
                    default:
                        break;
                }
                break;
            case "MESSAGE":
                printMessage(response[1].replace("\n","") + ": " + input.split(" ",3)[2].replace("\n","") + "\n");
                break;
            case "JOINED":
                printMessage(response[1].replace("\n","") + " joined the chat.\n");
                break;
            case "LEFT":
                printMessage(response[1].replace("\n","") + " left the chat.\n");
                break;
            case "PRIVATE":
                printMessage(response[1].replace("\n","") + " sent you a private message: " + input.split(" ",3)[2].replace("\n","") + "\n");
                break;
            case "NEWNICK":
                printMessage(response[1].replace("\n","") + " changed his nickname to " + response[2].replace("\n","") + "\n");
                break;
            case "BYE":
                printMessage("You left the server. Please close the Chat Client.\n");
                break;
        }
        
    }

    public void receiveMessages() {
        try {
            String input;
            while (true) {
               buffer.clear();
               if(s.read(buffer) >0){
                   buffer.flip();
                   input = decoder.decode(buffer).toString();
                   processInput(input);
               } 
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    // Método principal do objecto
    public void run() throws IOException {
        Thread t = new Thread(){
            public void run(){
                try{
                    receiveMessages();
                } catch (Exception e){
                }
            }
        };

        t.start();

    }
    

    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }

}
