package ru.otus.homework.workers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ru.otus.homework.messageSystem.Address;
import ru.otus.homework.messages.Message;
import ru.otus.homework.messages.MsgAddressInfo;
import ru.otus.homework.messages.MsgCreateUser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.*;

public class SocketMessageWorker implements MessageWorker {
    private static final int WORKER_COUNT = 2;

    private final ExecutorService executorService;
    private final Socket socket;

    private Address connectedAddress;
    
    private final BlockingQueue<Message> output = new LinkedBlockingQueue<>();
    private final BlockingQueue<Message> input = new LinkedBlockingQueue<>();

    public SocketMessageWorker(Socket socket) {
        this.socket = socket;
        executorService = Executors.newFixedThreadPool(WORKER_COUNT);
    }

    public void init() {
        executorService.execute(this::sendMessage);
        executorService.execute(this::receiveMessage);
    }

    @Override
    public Message pool() {
        return input.poll();
    }

    @Override
    public void send(Message message) {
        output.add(message);
    }

    @Override
    public Message take() throws InterruptedException {
        return input.take();
    }

    @Override
    public void close() throws IOException {
        socket.close();
        executorService.shutdown();
    }

    private void sendMessage(){
        try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true)){
            while (socket.isConnected()){
                Message message = output.take();
                String json = new Gson().toJson(message);
                out.println(json);
                out.println();
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void receiveMessage(){
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))){
            String inputLine;
            StringBuilder stringBuilder = new StringBuilder();
            while ((inputLine = reader.readLine()) != null){
                stringBuilder.append(inputLine);
                if (inputLine.isEmpty()){
                    String json = stringBuilder.toString();
                    Message message = getMessageFromGson(json);
                    
                    System.out.println("Get message: " + message);
                    
                    if (message.getTo() != null) {
                    	input.add(message);
                    }
                    else {
                    	this.connectedAddress = message.getFrom();
                    }
                    stringBuilder = new StringBuilder();
                }
            }
        }  catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

    }

    private Message getMessageFromGson(String json) throws ClassNotFoundException {

        JsonParser parser = new JsonParser();
        JsonObject jsonObject = (JsonObject) parser.parse(json);
        
        String className = String.valueOf(jsonObject.get(Message.CLASS_NAME_VARIABLE));
        
        Class<?> messageClass = null;
        
        if (className.contains(MsgCreateUser.class.getName())) {
        	messageClass = MsgCreateUser.class;
        }
        if (className.contains(MsgAddressInfo.class.getName())) {
        	messageClass = MsgAddressInfo.class;
        }
        
        return (Message) new Gson().fromJson(json, messageClass);
        
    }

	@Override
	public Address getConnectedAddress() {
		return connectedAddress;
	}
    
    
}
