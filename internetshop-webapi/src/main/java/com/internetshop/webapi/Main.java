package com.internetshop.webapi;

import com.sun.net.httpserver.HttpServer;

import java.nio.file.Path;

/**
 * Запуск: java com.internetshop.webapi.Main [порт] [папка-даних]
 * За замовчуванням — порт 8080 і папка data у робочій директорії.
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        Path dataDirectory = Path.of(args.length > 1 ? args[1] : "data");

        HttpServer server = InternetShopApp.start(port, dataDirectory);
        System.out.println("InternetShop запущено: http://localhost:" + port);
        System.out.println("Дані зберігаються в: " + dataDirectory.toAbsolutePath());
        System.out.println("Зупинити: Ctrl+C (або кнопка Stop в IntelliJ IDEA).");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
    }
}
