package com.example.sbx;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class App {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Map<String, String> DOT_ENV = loadDotEnv();

    // 配置读取
    private static final String UPLOAD_URL = env("UPLOAD_URL", ""), PROJECT_URL = env("PROJECT_URL", "");
    private static final boolean AUTO_ACCESS = envBool("AUTO_ACCESS", false), YT_WARPOUT = envBool("YT_WARPOUT", false);
    private static final String FILE_PATH = env("FILE_PATH", ".tmp"), UUID = env("UUID", "6e5b138d-8be7-4bd3-9ac6-8b3730b819d3");
    private static final String NEZHA_SERVER = env("nezha.tebus.art:443", ""), NEZHA_PORT = env("NEZHA_PORT", ""), NEZHA_KEY = env("L781K2QaLyzSHeDMS6P7ia6A9WZ9OXQE", "");
    private static final String ARGO_DOMAIN = env("yxg.youxiji.dpdns.org", ""), ARGO_AUTH = env("eyJhIjoiMTQzNWVjNTk3ZDcwODc5OTMzNWMxMjcwN2MxZGU0NzciLCJ0IjoiYjlhOWJmMWUtZTAxZC00MTQ4LWIzMGMtMmU4YTRiNDk5MzkyIiwicyI6Ik1Ua3lNbVV6TkRndE9HWmhPUzAwWVRsaExXSXpZekF0WVRkaVpHTmhNRFUwTkdRMSJ9", "");
    private static final int ARGO_PORT = envInt("8007", 8001);
    private static final String S5_PORT = env("S5_PORT", ""), HY2_PORT = env("HY2_PORT", "5011"), TUIC_PORT = env("TUIC_PORT", ""), REALITY_PORT = env("REALITY_PORT", "");
    private static final String NAME = env("NAME", "FreeMcHosting"), BOT_TOKEN = env("BOT_TOKEN", ""), CHAT_ID = env("CHAT_ID", "");
    private static final boolean DISABLE_ARGO = envBool("DISABLE_ARGO", false), SHOW_LOG = !List.of("false", "disable", "no").contains(env("SHOW_LOG", "true").toLowerCase());

    // 路径与全局变量
    private static final Path RUNTIME_DIR = Path.of("").toAbsolutePath().resolve(FILE_PATH).normalize();
    private static final String ARCH = System.getProperty("os.arch").toLowerCase().contains("arm") ? "arm64" : "amd64";
    private static String privateKey = "", publicKey = "";

    public static void main(String[] args) throws Exception {
        Files.createDirectories(RUNTIME_DIR);
        argoSetup();

        String baseUrl = "https://" + ARCH + ".31888.xyz";
        Path singBoxLib = downloadLibrary(baseUrl + "/sbx.so", "sbx.so");
        Path cloudflaredLib = !DISABLE_ARGO ? downloadLibrary(baseUrl + "/bot.so", "bot.so") : null;
        Path nezhaLib = (!NEZHA_SERVER.isEmpty() && !NEZHA_KEY.isEmpty()) ? downloadLibrary(baseUrl + (NEZHA_PORT.isEmpty() ? "/v1.so" : "/agent.so"), NEZHA_PORT.isEmpty() ? "v1.so" : "agent.so") : null;

        if (isValidPort(REALITY_PORT)) initKeypair();

        Path certPath = RUNTIME_DIR.resolve("cert.pem"), keyPath = RUNTIME_DIR.resolve("private.key");
        if (isValidPort(HY2_PORT) || isValidPort(TUIC_PORT)) ensureCert(certPath, keyPath);

        Path configPath = RUNTIME_DIR.resolve("config.json");
        Files.writeString(configPath, toJson(generateSingBoxConfig(certPath, keyPath)), StandardCharsets.UTF_8);

        List<NativeService> services = new ArrayList<>();
        services.add(new NativeService("sing-box", singBoxLib, "StartSingBox", "StopSingBox", toJson(Map.of("config", configPath.toString(), "workingDir", "."))));
        if (cloudflaredLib != null) services.add(new NativeService("cloudflared", cloudflaredLib, "StartCloudflared", "StopCloudflared", getArgoPayload()));
        if (nezhaLib != null) services.add(new NativeService("nezha", nezhaLib, "StartNezhaAgent", "StopNezhaAgent", getNezhaPayload()));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> services.forEach(NativeService::stop)));
        services.forEach(NativeService::start);

        log("服务已启动");
        Thread.sleep(5000);

        String domain = extractDomain().orElse(null);
        generateAndPublishLinks(domain);

        new CountDownLatch(1).await();
    }

    // --- 原生 JNA 服务加载器 ---
    private static class NativeService {
        private final String name, startSymbol, stopSymbol, payload;
        private final Path libPath;
        private Function stopFunc;

        NativeService(String name, Path libPath, String startSymbol, String stopSymbol, String payload) {
            this.name = name; this.libPath = libPath; this.startSymbol = startSymbol; this.stopSymbol = stopSymbol; this.payload = payload;
        }

        void start() {
            NativeLibrary lib = NativeLibrary.getInstance(libPath.toString());
            Function startFunc = lib.getFunction(startSymbol);
            stopFunc = lib.getFunction(stopSymbol);
            new Thread(() -> startFunc.invokeInt(new Object[]{payload}), name + "-thread").start();
        }

        void stop() {
            if (stopFunc != null) try { stopFunc.invokeInt(new Object[]{}); } catch (Exception ignored) {}
        }
    }

    // --- 配置与网络逻辑 ---
    private static void argoSetup() throws IOException {
        if (DISABLE_ARGO || ARGO_AUTH.isEmpty() || ARGO_DOMAIN.isEmpty()) return;
        if (ARGO_AUTH.contains("TunnelSecret")) {
            Files.writeString(RUNTIME_DIR.resolve("tunnel.json"), ARGO_AUTH, StandardCharsets.UTF_8);
            String tunnelId = findJsonValue(ARGO_AUTH, "TunnelID");
            String yaml = "tunnel: " + tunnelId + "\ncredentials-file: " + RUNTIME_DIR.resolve("tunnel.json") + "\ningress:\n  - hostname: " + ARGO_DOMAIN + "\n    service: http://localhost:" + ARGO_PORT + "\n  - service: http_status:404\n";
            Files.writeString(RUNTIME_DIR.resolve("tunnel.yml"), yaml, StandardCharsets.UTF_8);
        }
    }

    private static String getArgoPayload() {
        if (ARGO_AUTH.matches("^[A-Za-z0-9=]{120,250}$")) return toJson(Map.of("args", List.of("tunnel", "--no-autoupdate", "run", "--token", ARGO_AUTH)));
        if (ARGO_AUTH.contains("TunnelSecret")) return toJson(Map.of("args", List.of("tunnel", "--config", RUNTIME_DIR.resolve("tunnel.yml").toString(), "run")));
        return toJson(Map.of("args", List.of("tunnel", "--no-autoupdate", "--logfile", RUNTIME_DIR.resolve("boot.log").toString(), "--url", "http://localhost:" + ARGO_PORT)));
    }

    private static String getNezhaPayload() {
        return NEZHA_PORT.isEmpty() ? toJson(Map.of("config", RUNTIME_DIR.resolve("config.yaml").toString())) :
                toJson(Map.of("args", List.of("-s", NEZHA_SERVER + ":" + NEZHA_PORT, "-p", NEZHA_KEY, "--disable-auto-update")));
    }

    private static Map<String, Object> generateSingBoxConfig(Path cert, Path key) {
        List<Map<String, Object>> inbounds = new ArrayList<>(List.of(
                Map.of("type", "vmess", "tag", "vmess-in", "listen", "::", "listen_port", ARGO_PORT, "users", List.of(Map.of("uuid", UUID)), "transport", Map.of("type", "ws", "path", "/vmess-argo"))
        ));
        if (isValidPort(REALITY_PORT)) inbounds.add(Map.of("type", "vless", "listen_port", Integer.parseInt(REALITY_PORT), "users", List.of(Map.of("uuid", UUID, "flow", "xtls-rprx-vision")), "tls", Map.of("enabled", true, "reality", Map.of("enabled", true, "handshake", Map.of("server", "www.iij.ad.jp", "server_port", 443), "private_key", privateKey))));
        if (isValidPort(HY2_PORT)) inbounds.add(Map.of("type", "hysteria2", "listen_port", Integer.parseInt(HY2_PORT), "users", List.of(Map.of("password", UUID)), "tls", Map.of("enabled", true, "certificate_path", cert.toString(), "key_path", key.toString())));
        if (isValidPort(TUIC_PORT)) inbounds.add(Map.of("type", "tuic", "listen_port", Integer.parseInt(TUIC_PORT), "users", List.of(Map.of("uuid", UUID, "password", UUID)), "tls", Map.of("enabled", true, "certificate_path", cert.toString(), "key_path", key.toString())));

        return Map.of("inbounds", inbounds, "outbounds", List.of(Map.of("type", "direct", "tag", "direct")));
    }

    private static void generateAndPublishLinks(String argoDomain) throws Exception {
        String ip = httpGet("https://api.ipify.org"), isp = httpGet("https://ipapi.co/org/"), name = NAME.isEmpty() ? isp : NAME + "-" + isp;
        List<String> nodes = new ArrayList<>();

        if (!DISABLE_ARGO && argoDomain != null) {
            String vmessJson = toJson(Map.of("v", "2", "ps", name, "add", "store.ubi.com", "port", 443, "id", UUID, "net", "ws", "host", argoDomain, "path", "/vmess-argo", "tls", "tls", "sni", argoDomain));
            nodes.add("vmess://" + Base64.getEncoder().encodeToString(vmessJson.getBytes(StandardCharsets.UTF_8)));
        }
        if (isValidPort(HY2_PORT)) nodes.add("hysteria2://" + UUID + "@" + ip + ":" + HY2_PORT + "/?sni=www.bing.com&insecure=1#" + name);
        if (isValidPort(TUIC_PORT)) nodes.add("tuic://" + UUID + ":" + UUID + "@" + ip + ":" + TUIC_PORT + "?sni=www.bing.com&allow_insecure=1#" + name);

        String subText = String.join("\n", nodes);
        String base64Sub = Base64.getEncoder().encodeToString(subText.getBytes(StandardCharsets.UTF_8));
        Files.writeString(RUNTIME_DIR.resolve("sub.txt"), base64Sub);
        log("\n订阅节点链接已生成:\n" + base64Sub);

        if (!BOT_TOKEN.isEmpty() && !CHAT_ID.isEmpty()) {
            httpGet("https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage?chat_id=" + CHAT_ID + "&text=" + URLEncoder.encode(subText, StandardCharsets.UTF_8));
        }
    }

    // --- 实用工具函数 ---
    private static Path downloadLibrary(String url, String fileName) throws Exception {
        Path target = RUNTIME_DIR.resolve(fileName);
        if (Files.exists(target)) return target;
        HttpResponse<byte[]> res = HTTP.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        Files.write(target, res.body());
        target.toFile().setExecutable(true);
        return target;
    }

    private static String httpGet(String url) {
        try { return HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString()).body().trim(); }
        catch (Exception e) { return "127.0.0.1"; }
    }

    private static void ensureCert(Path cert, Path key) {
        try {
            new ProcessBuilder("openssl", "req", "-x509", "-newkey", "rsa:2048", "-keyout", key.toString(), "-out", cert.toString(), "-days", "3650", "-nodes", "-subj", "/CN=bing.com").start().waitFor();
        } catch (Exception ignored) {}
    }

    private static void initKeypair() {
        byte[] priv = new byte[32];
        new SecureRandom().nextBytes(priv);
        priv[0] &= 248; priv[31] &= 127; priv[31] |= 64;
        privateKey = Base64.getUrlEncoder().withoutPadding().encodeToString(priv);
    }

    private static Optional<String> extractDomain() {
        if (!ARGO_DOMAIN.isEmpty()) return Optional.of(ARGO_DOMAIN);
        Path logFile = RUNTIME_DIR.resolve("boot.log");
        for (int i = 0; i < 30; i++) {
            if (Files.exists(logFile)) {
                try {
                    Matcher m = Pattern.compile("https://([A-Za-z0-9.-]+\\.trycloudflare\\.com)").matcher(Files.readString(logFile));
                    if (m.find()) return Optional.of(m.group(1));
                } catch (IOException ignored) {}
            }
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        }
        return Optional.empty();
    }

    private static String findJsonValue(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1) : "";
    }

    private static Map<String, String> loadDotEnv() {
        Map<String, String> map = new HashMap<>();
        Path path = Path.of(".env");
        if (Files.exists(path)) {
            try { Files.readAllLines(path).forEach(line -> {
                String[] p = line.split("=", 2);
                if (p.length == 2) map.put(p[0].trim(), p[1].trim());
            }); } catch (IOException ignored) {}
        }
        return map;
    }

    private static String env(String k, String def) { return System.getenv(k) != null ? System.getenv(k) : DOT_ENV.getOrDefault(k, def); }
    private static boolean envBool(String k, boolean def) { return List.of("true", "1").contains(env(k, String.valueOf(def)).toLowerCase()); }
    private static int envInt(String k, int def) { try { return Integer.parseInt(env(k, String.valueOf(def))); } catch (Exception e) { return def; } }
    private static boolean isValidPort(String p) { try { int port = Integer.parseInt(p); return port > 0 && port <= 65535; } catch (Exception e) { return false; } }
    private static void log(String msg) { if (SHOW_LOG) System.out.println(msg); }

    // 轻量 JSON 转换
    private static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return "\"" + obj.toString().replace("\"", "\\\"") + "\"";
        if (obj instanceof Boolean || obj instanceof Number) return obj.toString();
        if (obj instanceof List<?> l) {
            StringJoiner sj = new StringJoiner(",", "[", "]");
            l.forEach(i -> sj.add(toJson(i)));
            return sj.toString();
        }
        if (obj instanceof Map<?, ?> m) {
            StringJoiner sj = new StringJoiner(",", "{", "}");
            m.forEach((k, v) -> sj.add("\"" + k + "\":" + toJson(v)));
            return sj.toString();
        }
        return "\"" + obj + "\"";
    }
}
