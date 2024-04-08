package org.example.server;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.stream.Collectors;

public class Server {

  // http -> 8080
  // https -> 443
  // smtp -> 25
  // ...

  public static final int PORT = 8181;

  public static void main(String[] args) {
    final Map<String, ClientHandler> clients = new HashMap<>();

    try (ServerSocket serverSocket = new ServerSocket(PORT)) {
      System.out.println("Сервер запущен на порту " + PORT);
      while (true) {
        try {
          Socket clientSocket = serverSocket.accept();
          System.out.println("Подключился новый клиент: " + clientSocket);

          PrintWriter clientOut = new PrintWriter(clientSocket.getOutputStream(), true);
          clientOut.println("Подключение успешно. Пришлите свой идентификатор");

          Scanner clientIn = new Scanner(clientSocket.getInputStream());
          String clientId = clientIn.nextLine();
          System.out.println("Идентификатор клиента " + clientSocket + ": " + clientId);

          String allClients = clients.entrySet().stream()
                  .map(it -> "id = " + it.getKey() + ", client = " + it.getValue().getClientSocket())
                  .collect(Collectors.joining("\n"));
          clientOut.println("Список доступных клиентов: \n" + allClients);

          ClientHandler clientHandler = new ClientHandler(clientSocket, clients);
          new Thread(clientHandler).start();

          for (ClientHandler client : clients.values()) {
            client.send("Подключился новый клиент: " + clientSocket + ", id = " + clientId);
          }
          clients.put(clientId, clientHandler);
        } catch (IOException e) {
          System.err.println("Произошла ошибка при взаимодействии с клиентом: " + e.getMessage());
        }
      }
    } catch (IOException e) {
      throw new RuntimeException("Не удалось начать прослушивать порт " + PORT, e);
    }
  }

}

class ClientHandler implements Runnable {

  private final Socket clientSocket;
  private final PrintWriter out;
  private final Map<String, ClientHandler> clients;

  public ClientHandler(Socket clientSocket, Map<String, ClientHandler> clients) throws IOException {
    this.clientSocket = clientSocket;
    this.out = new PrintWriter(clientSocket.getOutputStream(), true);
    this.clients = clients;
  }

  public Socket getClientSocket() {
    return clientSocket;
  }

  @Override
  public void run() {
    try (Scanner in = new Scanner(clientSocket.getInputStream())) {
      while (true) {
        if (clientSocket.isClosed()) {
          System.out.println("Клиент " + clientSocket + " отключился");
          // Отправляем уведомление об отключении клиента всем остальным клиентам
          for (ClientHandler client : clients.values()) {
            if (!client.equals(this)) {
              client.send("Клиент " + clientSocket + " отключился");
            }
          }
          break;
        }

        String input = in.nextLine();
        System.out.println("Получено сообщение от клиента " + clientSocket + ": " + input);

        // Обработка системных вызовов
        if (input.startsWith("/")) {
          handleSystemCommand(input);
          continue;
        }

        String toClientId = null;
        if (input.startsWith("@")) {
          String[] parts = input.split("\\s+");
          if (parts.length > 0) {
            toClientId = parts[0].substring(1);
          }
        }

        if (toClientId == null) {
          clients.values().forEach(it -> it.send(input));
        } else {
          ClientHandler toClient = clients.get(toClientId);
          if (toClient != null) {
            toClient.send(input.replace("@" + toClientId + " ", ""));
          } else {
            System.err.println("Не найден клиент с идентификатором: " + toClientId);
          }
        }

        out.println("Сообщение [" + input + "] получено");
        if (Objects.equals("/exit", input)) {
          handleSystemCommand("/exit");
          break;
        }
      }
    } catch (IOException e) {
      System.err.println("Произошла ошибка при взаимодействии с клиентом " + clientSocket + ": " + e.getMessage());
    }

    // Удаляем отключившегося клиента из Map
    clients.remove(this);
    try {
      clientSocket.close();
    } catch (IOException e) {
      System.err.println("Ошибка при отключении клиента " + clientSocket + ": " + e.getMessage());
    }

  }

  public void send(String msg) {
    out.println(msg);
  }

  private void handleSystemCommand(String command) {
    switch (command) {
      case "/all":
        send("Список текущих пользователей: " + clients.keySet());
        break;
      case "/exit":
        clients.remove(this);
        out.println("exit"); // Посылаем клиенту сигнал о выходе
        break;
      default:
        send("Неверная команда: " + command);
        break;
    }
  }
}
