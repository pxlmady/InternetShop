package com.internetshop.webapi;

import com.internetshop.infrastructure.caching.MemcachedOptions;
import com.sun.net.httpserver.HttpServer;

import java.nio.file.Path;

/**
 * Запуск: java com.internetshop.webapi.Main [порт] [папка-даних] [memcached-host:port]
 * За замовчуванням — порт 8080, папка data у робочій директорії і Memcached на 127.0.0.1:11211
 * (адресу можна задати й змінною середовища INTERNETSHOP_MEMCACHED). Якщо Memcached не запущено,
 * застосунок працює без кешу — усі читання йдуть у сховище.
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        Path dataDirectory = Path.of(args.length > 1 ? args[1] : "data");
        String memcachedServer = args.length > 2 ? args[2]
                : System.getenv().getOrDefault("INTERNETSHOP_MEMCACHED", MemcachedOptions.DEFAULT_SERVER);
        MemcachedOptions memcached = MemcachedOptions.parse(memcachedServer);

        HttpServer server = InternetShopApp.start(port, dataDirectory, memcached);
        System.out.println("InternetShop запущено: http://localhost:" + port);
        System.out.println("Дані зберігаються в: " + dataDirectory.toAbsolutePath());
        System.out.println("Розподілений кеш (Memcached): " + memcached.server());
        System.out.println("Зупинити: Ctrl+C (або кнопка Stop в IntelliJ IDEA).");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
    }
}
