package com.example.sbx;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class App {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final Map<String, String> DOT_ENV = loadDotEnv();

    private static final String PROJECT_URL = env("PROJECT_URL", "");
    private static final boolean AUTO_ACCESS = envBool("AUTO_ACCESS", false);
    private static final boolean YT_WARPOUT = envBool("YT_WARPOUT", false);
    private static final String FILE_PATH = env("FILE_PATH", ".tmp");
    private static final String SUB_PATH = env("SUB_PATH", "sub");
    private static final String UUID = env("UUID", "6e5b138d-8be7-4bd3-9ac6-8b3730b819d3");
    private static final String NEZHA_SERVER = env("nezha.tebus.art:443", "");
    private static final String NEZHA_PORT = env("NEZHA_PORT", "");
    private static final String NEZHA_KEY = env("L781K2QaLyzSHeDMS6P7ia6A9WZ9OXQE", "");
    private static final String ARGO_DOMAIN = env("yxg.youxiji.dpdns.org", "");
    private static final String ARGO_AUTH = env("eyJhIjoiMTQzNWVjNTk3ZDcwODc5OTMzNWMxMjcwN2MxZGU0NzciLCJ0IjoiYjlhOWJmMWUtZTAxZC00MTQ4LWIzMGMtMmU4YTRiNDk5MzkyIiwicyI6Ik1Ua3lNbVV6TkRndE9HWmhPUzAwWVRsaExXSXpZekF0WVRkaVpHTmhNRFUwTkdRMSJ9", "");
    private static final int ARGO_PORT = envInt("8007", 8001);
    private static final String S5_PORT = env("S5_PORT", "");
    private static final String HY2_PORT = env("HY2_PORT", "5011");
    private static final String CFIP = env("CFIP", "store.ubi.com");
    private static final int CFPORT = envInt("CFPORT", 443);
    private static final String NAME = env("NAME", "FreeMcHosting");
    private static final boolean DISABLE_ARGO = envBool("DISABLE_ARGO", false);
    private static final boolean SHOW_LOG = !List.of("false", "disable", "no").contains(env("SHOW_LOG", "true").toLowerCase());

    private static final Path ROOT = Path.of("").toAbsolutePath();
    private static final Path RUNTIME_DIR = ROOT.resolve(FILE_PATH).normalize();
    private static final Path SING_BOX_CONFIG_PATH = RUNTIME_DIR.resolve("config.json");
    private static final Path NEZHA_CONFIG_PATH = RUNTIME_DIR.resolve("config.yaml");
    private static final Path BOOT_LOG_PATH = RUNTIME_DIR.resolve("boot.log");
    private static final Path SUB_FILE_PATH = RUNTIME_DIR.resolve("sub.txt");
    private static final Path LIST_FILE_PATH = RUNTIME_DIR.resolve("list.txt");
    private static final Path INDEX_FILE_PATH = ROOT.resolve("index.html").normalize();
    private static final String SUBSCRIBE_PATH = "/" + SUB_PATH.replaceFirst("^/+", "");
    private static final String ARCH = detectArch();

    private static final String FALLBACK_EC_KEY =
            "-----BEGIN EC PRIVATE KEY-----\n" +
            "MHcCAQEEIIn2Ylh/uU0eL6yGqDpxo3fD1L2q2X5L8vK2p3y4M+I1oAoGCCqGSM49\n" +
            "AwEHoUQDAgAEAyAC/1fP6O5a4mR9sL4l8s5J7k1J1K1L1M1N1O1P1Q1R1S1T1U1V\n" +
            "1W1X1Y1Z1a1b1c1d1e1f1g==\n" +
            "-----END EC PRIVATE KEY-----\n";

    private static final String FALLBACK_CERT =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIB3jCCAYSgAwIBAgIUa1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6MAoGCCqGSM49\n" +
            "BAMCMAMxCzAJBgNVBAYTAkNOADAeFw0yNDAxMDEwMDAwMDBaFw0zNDAxMDEwMDAw\n" +
            "MDBaMAMxCzAJBgNVBAYTAkNOMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEAyAC\n" +
            "/1fP6O5a4mR9sL4l8s5J7k1J1K1L1M1N1O1P1Q1R1S1T1U1V1W1X1Y1Z1a1b1c1d\n" +
            "1e1f1g==\n" +
            "-----END CERTIFICATE-----\n";

    public static void main(String[] args) throws Exception {
        startServer();
    }

    private static void startServer() throws Exception {
        Files.createDirectories(RUNTIME_DIR);
        cleanupOldFiles();
        argoType();

        String baseUrl = "https://" + ARCH + ".31888.xyz";
        Path singBoxLib = downloadLibrary(baseUrl + "/sbx.so", "sbx.so");
        Path cloudflaredLib = null;
        Path nezhaLib = null;
        Path nezhaAgentLib = null;

        if (!DISABLE_ARGO) {
            cloudflaredLib = downloadLibrary(baseUrl + "/bot.so", "bot.so");
        }
        if (!NEZHA_SERVER.isEmpty() && !NEZHA_KEY.isEmpty() && !NEZHA_PORT.isEmpty()) {
            nezhaAgentLib = downloadLibrary(baseUrl + "/agent.so", "agent.so");
        } else if (!NEZHA_SERVER.isEmpty() && !NEZHA_KEY.isEmpty()) {
            nezhaLib = downloadLibrary(baseUrl + "/v1.so", "v1.so");
        } else {
            log("NEZHA 变量为空，跳过加载");
        }

        Path certPath = RUNTIME_DIR.resolve("cert.pem");
        Path keyPath = RUNTIME_DIR.resolve("private.key");
        if (isValidPort(HY2_PORT)) {
            ensureTlsCertificates(certPath, keyPath);
        }

        if (!NEZHA_SERVER.isEmpty() && !NEZHA_KEY.isEmpty() && NEZHA_PORT.isEmpty()) {
            generateNezhaConfig();
        }

        Files.writeString(SING_BOX_CONFIG_PATH, toJson(generateSingBoxConfig(certPath.toString(), keyPath.toString())), StandardCharsets.UTF_8);

        List<NativeService> services = new ArrayList<>();
        services.add(new NativeService("sing-box", singBoxLib, "StartSingBox", "StopSingBox", singboxPayload()));
        if (cloudflaredLib != null) {
            String payload = cloudflaredPayload();
            if (payload != null) {
                services.add(new NativeService("cloudflared", cloudflaredLib, "StartCloudflared", "StopCloudflared", payload));
            }
        }
        if (nezhaLib != null) {
            services.add(new NativeService("nezha-agent", nezhaLib, "StartNezhaAgent", "StopNezhaAgent", nezhaPayload()));
        } else if (nezhaAgentLib != null) {
            services.add(new NativeService("nezha-agent", nezhaAgentLib, "StartNezhaAgent", "StopNezhaAgent", nezhaV0Payload()));
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> stopAll(services), "shutdown-hook"));
        for (NativeService service : services) {
            service.start();
        }

        sleep(1000);
        log("web 服务运行中");
        if (cloudflaredLib != null) log("bot 服务运行中");
        if (nezhaLib != null || nezhaAgentLib != null) log("php 服务运行中");

        sleep(5000);
        String argoDomain = extractDomain().orElse(null);
        String subText = generateLinks(argoDomain);

        addVisitTask();

        Thread cleanupThread = new Thread(() -> {
            sleep(45000);
            cleanupFiles(true);
            clearConsole();
        }, "delayed-cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();

        new CountDownLatch(1).await();
    }

    private static void stopAll(List<NativeService> services) {
        log("\n正在停止所有服务...");
        for (int i = services.size() - 1; i >= 0; i--) {
            try {
                services.get(i).stop();
            } catch (Exception ignored) {
            }
        }
    }

    private static class NativeService {
        private final String name;
        private final Path libPath;
        private final String startSymbol;
        private final String stopSymbol;
        private final String payload;
        private NativeLibrary library;
        private Function stopFunction;
        private boolean running;

        NativeService(String name, Path libPath, String startSymbol, String stopSymbol, String payload) {
            this.name = name;
            this.libPath = libPath;
            this.startSymbol = startSymbol;
            this.stopSymbol = stopSymbol;
            this.payload = payload == null ? "" : payload;
        }

        void start() {
            library = NativeLibrary.getInstance(libPath.toString());
            Function startFunction = library.getFunction(startSymbol);
            stopFunction = library.getFunction(stopSymbol);
            Thread thread = new Thread(() -> {
                try {
                    int code = startFunction.invokeInt(new Object[]{payload});
                    if (code != 0) {
                        log(name + " 原生服务退出，退出代码: " + code);
                    }
                } catch (Exception e) {
                    log(name + " 原生服务启动失败: " + e.getMessage());
                }
            }, name + "-thread");
            thread.setDaemon(true);
            thread.start();
            running = true;
        }

        void stop() {
            if (!running || stopFunction == null) return;
            try {
                int code = stopFunction.invokeInt(new Object[]{});
                running = false;
                log(name + " 已停止，代码: " + code);
            } catch (Exception e) {
                log("停止 " + name + " 失败: " + e.getMessage());
            }
        }
    }

    private static void argoType() throws IOException {
        if (DISABLE_ARGO) {
            log("DISABLE_ARGO 已设置为 true，禁用 Argo 隧道");
            return;
        }
        if (ARGO_AUTH.isEmpty() || ARGO_DOMAIN.isEmpty()) {
            log("ARGO_DOMAIN 或 ARGO_AUTH 变量为空，使用快捷隧道");
            return;
        }
        if (ARGO_AUTH.contains("TunnelSecret")) {
            Files.writeString(RUNTIME_DIR.resolve("tunnel.json"), ARGO_AUTH, StandardCharsets.UTF_8);
            String tunnelId = findJsonString(ARGO_AUTH, "TunnelID").orElse("");
            String yaml = "tunnel: " + tunnelId + "\n" +
                    "credentials-file: " + RUNTIME_DIR.resolve("tunnel.json") + "\n" +
                    "protocol: http2\n\n" +
                    "ingress:\n" +
                    "  - hostname: " + ARGO_DOMAIN + "\n" +
                    "    service: http://localhost:" + ARGO_PORT + "\n" +
                    "    originRequest:\n" +
                    "    noTLSVerify: true\n" +
                    "  - service: http_status:404\n";
            Files.writeString(RUNTIME_DIR.resolve("tunnel.yml"), yaml, StandardCharsets.UTF_8);
        } else {
            log("使用 Token 连接隧道，请在 Cloudflare 中配置端口 " + ARGO_PORT);
        }
    }

    private static Path downloadLibrary(String url, String fileName) throws Exception {
        Path target = RUNTIME_DIR.resolve(fileName);
        if (Files.exists(target)) {
            log("使用本地缓存的原生动态库: " + target);
            return target;
        }
        Files.createDirectories(RUNTIME_DIR);
        Path tmp = RUNTIME_DIR.resolve(fileName + ".download");
        log("下载中: " + url + " -> " + target);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(3)).GET().build();
        HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("下载失败 " + url + ": HTTP " + response.statusCode());
        }
        Files.write(tmp, response.body());
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        target.toFile().setExecutable(true, false);
        return target;
    }

    private static Map<String, Object> generateSingBoxConfig(String certPath, String keyPath) {
        List<Object> inbounds = new ArrayList<>();
        inbounds.add(mapOf(
                "type", "vmess",
                "tag", "vmess-ws-in",
                "listen", "::",
                "listen_port", ARGO_PORT,
                "users", listOf(mapOf("uuid", UUID)),
                "transport", mapOf("type", "ws", "path", "/vmess-argo", "early_data_header_name", "Sec-WebSocket-Protocol")
        ));

        if (isValidPort(HY2_PORT)) {
            inbounds.add(mapOf(
                    "type", "hysteria2",
                    "tag", "hysteria-in",
                    "listen", "::",
                    "listen_port", Integer.parseInt(HY2_PORT),
                    "users", listOf(mapOf("password", UUID)),
                    "masquerade", "https://bing.com",
                    "tls", mapOf("enabled", true, "alpn", listOf("h3"), "certificate_path", certPath, "key_path", keyPath)
            ));
        }

        if (isValidPort(S5_PORT)) {
            inbounds.add(mapOf(
                    "type", "socks",
                    "tag", "s5-in",
                    "listen", "::",
                    "listen_port", Integer.parseInt(S5_PORT),
                    "users", listOf(mapOf("username", UUID.substring(0, 8), "password", UUID.substring(UUID.length() - 12)))
            ));
        }

        List<Object> ruleSet = new ArrayList<>();
        ruleSet.add(mapOf("tag", "netflix", "type", "remote", "format", "binary", "url", "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/sing/geo/geosite/netflix.srs"));
        ruleSet.add(mapOf("tag", "openai", "type", "remote", "format", "binary", "url", "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/sing/geo/geosite/openai.srs"));
        List<Object> wireguardRuleSets = new ArrayList<>(listOf("netflix"));
        if (needsYoutubeWarp()) {
            ruleSet.add(mapOf("tag", "youtube", "type", "remote", "format", "binary", "url", "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/sing/geo/geosite/youtube.srs"));
            wireguardRuleSets.add("youtube");
            log("已添加 YouTube 分流规则");
        }

        List<Object> endpoints = listOf(mapOf(
                "type", "wireguard",
                "tag", "wireguard-out",
                "mtu", 1280,
                "address", listOf("172.16.0.2/32", "2606:4700:110:8dfe:d141:69bb:6b80:925/128"),
                "private_key", "YFYOAdbw1bKTHlNNi+aEjBM3BO7unuFC5rOkMRAz9XY=",
                "peers", listOf(mapOf(
                        "address", "engage.cloudflareclient.com",
                        "port", 2408,
                        "public_key", "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=",
                        "allowed_ips", listOf("0.0.0.0/0", "::/0"),
                        "reserved", listOf(78, 135, 76)
                ))
        ));

        return mapOf(
                "log", mapOf("disabled", true, "level", "error", "timestamp", true),
                "http_clients", listOf(mapOf("tag", "http-client-direct")),
                "inbounds", inbounds,
                "endpoints", endpoints,
                "outbounds", listOf(mapOf("type", "direct", "tag", "direct")),
                "route", mapOf(
                        "default_http_client", "http-client-direct",
                        "rule_set", ruleSet,
                        "rules", listOf(mapOf("rule_set", wireguardRuleSets, "outbound", "wireguard-out")),
                        "final", "direct"
                )
        );
    }

    private static String cloudflaredPayload() {
        if (DISABLE_ARGO) return null;
        if (!ARGO_AUTH.isEmpty() && !ARGO_DOMAIN.isEmpty()) {
            if (Pattern.matches("^[A-Za-z0-9=]{120,250}$", ARGO_AUTH)) {
                return toJson(mapOf("args", listOf("tunnel", "--edge-ip-version", "auto", "--no-autoupdate", "--protocol", "http2", "run", "--token", ARGO_AUTH)));
            }
            if (ARGO_AUTH.contains("TunnelSecret")) {
                return toJson(mapOf("args", listOf("tunnel", "--edge-ip-version", "auto", "--config", RUNTIME_DIR.resolve("tunnel.yml").toString(), "run")));
            }
        }
        return toJson(mapOf("args", listOf("tunnel", "--edge-ip-version", "auto", "--no-autoupdate", "--protocol", "http2", "--logfile", BOOT_LOG_PATH.toString(), "--loglevel", "info", "--url", "http://localhost:" + ARGO_PORT)));
    }

    private static String singboxPayload() {
        return toJson(mapOf("config", SING_BOX_CONFIG_PATH.toString(), "workingDir", ".", "disableColor", true));
    }

    private static String nezhaPayload() {
        return toJson(mapOf("config", NEZHA_CONFIG_PATH.toString()));
    }

    private static String nezhaV0Payload() {
        List<Object> args = new ArrayList<>(listOf("-s", NEZHA_SERVER + ":" + NEZHA_PORT, "-p", NEZHA_KEY, "--disable-auto-update", "--report-delay", "4", "--skip-conn", "--skip-procs"));
        if (List.of("443", "8443", "2096", "2087", "2083", "2053").contains(NEZHA_PORT)) {
            args.add("--tls");
        }
        return toJson(mapOf("args", args));
    }

    private static void generateNezhaConfig() throws IOException {
        String nzPort = NEZHA_SERVER.contains(":") ? NEZHA_SERVER.substring(NEZHA_SERVER.lastIndexOf(':') + 1) : "";
        boolean tls = List.of("443", "8443", "2096", "2087", "2083", "2053").contains(nzPort);
        String yaml = "client_secret: " + NEZHA_KEY + "\n" +
                "debug: false\n" +
                "disable_auto_update: true\n" +
                "disable_command_execute: false\n" +
                "disable_force_update: true\n" +
                "disable_nat: false\n" +
                "disable_send_query: false\n" +
                "gpu: false\n" +
                "insecure_tls: true\n" +
                "ip_report_period: 1800\n" +
                "report_delay: 4\n" +
                "server: " + NEZHA_SERVER + "\n" +
                "skip_connection_count: true\n" +
                "skip_procs_count: true\n" +
                "temperature: false\n" +
                "tls: " + tls + "\n" +
                "use_gitee_to_upgrade: false\n" +
                "use_ipv6_country_code: false\n" +
                "uuid: " + UUID;
        Files.writeString(NEZHA_CONFIG_PATH, yaml, StandardCharsets.UTF_8);
    }

    private static String generateLinks(String argoDomain) throws Exception {
        String serverIp = getServerIp();
        String isp = getMetaInfo();
        String nodeName = NAME.isEmpty() ? isp : NAME + "-" + isp;
        sleep(2000);

        List<String> nodes = new ArrayList<>();
        if (!DISABLE_ARGO && argoDomain != null && !argoDomain.isEmpty()) {
            Map<String, Object> vmess = mapOf(
                    "v", "2", "ps", nodeName, "add", CFIP, "port", CFPORT, "id", UUID,
                    "aid", "0", "scy", "auto", "net", "ws", "type", "none",
                    "host", argoDomain, "path", "/vmess-argo?ed=2560", "tls", "tls",
                    "sni", argoDomain, "alpn", "", "fp", "firefox"
            );
            nodes.add("vmess://" + Base64.getEncoder().encodeToString(toJson(vmess).getBytes(StandardCharsets.UTF_8)));
        }
        if (isValidPort(HY2_PORT)) {
            nodes.add("hysteria2://" + UUID + "@" + serverIp + ":" + HY2_PORT + "/?sni=www.bing.com&insecure=1&alpn=h3&obfs=none#" + nodeName);
        }
        if (isValidPort(S5_PORT)) {
            String auth = Base64.getEncoder().encodeToString((UUID.substring(0, 8) + ":" + UUID.substring(UUID.length() - 12)).getBytes(StandardCharsets.UTF_8));
            nodes.add("socks://" + auth + "@" + serverIp + ":" + S5_PORT + "#" + nodeName);
        }

        String subText = String.join("\n", nodes);
        String encoded = Base64.getEncoder().encodeToString(subText.getBytes(StandardCharsets.UTF_8));
        log("\u001b[32m" + encoded + "\u001b[0m");
        log("\u001b[35m日志将在 45 秒内清除，请及时复制上方节点\u001b[0m");
        Files.writeString(SUB_FILE_PATH, encoded, StandardCharsets.UTF_8);
        Files.writeString(LIST_FILE_PATH, subText, StandardCharsets.UTF_8);
        log(FILE_PATH + "/sub.txt 保存成功");
        return subText;
    }

    private static Optional<String> extractDomain() {
        if (DISABLE_ARGO) return Optional.empty();
        if (!ARGO_AUTH.isEmpty() && !ARGO_DOMAIN.isEmpty()) {
            log("ARGO 域名: " + ARGO_DOMAIN);
            return Optional.of(ARGO_DOMAIN);
        }
        log("正在日志中等待快捷隧道域名...");
        Optional<String> domain = waitForQuickTunnelDomain(Duration.ofSeconds(30));
        if (domain.isEmpty()) {
            log("未找到快捷隧道域名，重试中...");
            try { Files.deleteIfExists(BOOT_LOG_PATH); } catch (IOException ignored) {}
            sleep(5000);
            domain = waitForQuickTunnelDomain(Duration.ofSeconds(30));
        }
        domain.ifPresentOrElse(d -> log("Argo 域名: " + d), () -> log("未获取到 Argo 域名"));
        return domain;
    }

    private static Optional<String> waitForQuickTunnelDomain(Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        Pattern pattern = Pattern.compile("https://([A-Za-z0-9.-]+\\.trycloudflare\\.com)");
        String last = "";
        while (System.currentTimeMillis() < deadline) {
            try {
                if (Files.exists(BOOT_LOG_PATH)) {
                    String content = Files.readString(BOOT_LOG_PATH, StandardCharsets.UTF_8);
                    if (!content.equals(last)) {
                        last = content;
                        Matcher matcher = pattern.matcher(content);
                        String found = null;
                        while (matcher.find()) found = matcher.group(1);
                        if (found != null) return Optional.of(found);
                    }
                }
            } catch (IOException ignored) {
            }
            sleep(1000);
        }
        return Optional.empty();
    }

    private static void ensureTlsCertificates(Path certPath, Path keyPath) throws IOException {
        if (Files.exists(certPath) && Files.exists(keyPath) && looksLikePemPair(certPath, keyPath)) return;
        Files.createDirectories(certPath.getParent());
        Path tmpCert = Path.of(certPath + ".tmp");
        Path tmpKey = Path.of(keyPath + ".tmp");
        Files.deleteIfExists(tmpCert);
        Files.deleteIfExists(tmpKey);
        try {
            if (runCommand("openssl", "version") == 0 &&
                    runCommand("openssl", "ecparam", "-genkey", "-name", "prime256v1", "-out", tmpKey.toString()) == 0 &&
                    runCommand("openssl", "req", "-new", "-x509", "-days", "3650", "-key", tmpKey.toString(), "-out", tmpCert.toString(), "-subj", "/CN=bing.com") == 0 &&
                    looksLikePemPair(tmpCert, tmpKey)) {
                Files.move(tmpCert, certPath, StandardCopyOption.REPLACE_EXISTING);
                Files.move(tmpKey, keyPath, StandardCopyOption.REPLACE_EXISTING);
                return;
            }
        } catch (Exception ignored) {
        } finally {
            Files.deleteIfExists(tmpCert);
            Files.deleteIfExists(tmpKey);
        }
        Files.writeString(keyPath, FALLBACK_EC_KEY, StandardCharsets.UTF_8);
        Files.writeString(certPath, FALLBACK_CERT, StandardCharsets.UTF_8);
        if (!looksLikePemPair(certPath, keyPath)) throw new IOException("生成有效的 TLS 证书对失败");
    }

    private static boolean looksLikePemPair(Path certPath, Path keyPath) {
        try {
            String cert = Files.readString(certPath, StandardCharsets.UTF_8);
            String key = Files.readString(keyPath, StandardCharsets.UTF_8);
            return cert.contains("-----BEGIN CERTIFICATE-----") && cert.contains("-----END CERTIFICATE-----") &&
                    key.contains("-----BEGIN EC PRIVATE KEY-----") && key.contains("-----END EC PRIVATE KEY-----");
        } catch (IOException e) {
            return false;
        }
    }

    private static void addVisitTask() {
        if (!AUTO_ACCESS || PROJECT_URL.isEmpty()) return;
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    HttpRequest req = HttpRequest.newBuilder(URI.create(PROJECT_URL)).GET().build();
                    HTTP.send(req, HttpResponse.BodyHandlers.discarding());
                } catch (Exception ignored) {}
                sleep(120000);
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private static void cleanupOldFiles() {
        cleanupFiles(false);
    }

    private static void cleanupFiles(boolean deleteLogs) {
        try {
            if (deleteLogs) Files.deleteIfExists(BOOT_LOG_PATH);
        } catch (IOException ignored) {}
    }

    private static void clearConsole() {
        if (!SHOW_LOG) {
            System.out.print("\033[H\033[2J");
            System.out.flush();
        }
    }

    private static String getServerIp() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.ipify.org")).timeout(Duration.ofSeconds(5)).GET().build();
            return HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body().trim();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private static String getMetaInfo() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://ipapi.co/org/")).timeout(Duration.ofSeconds(5)).GET().build();
            return HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body().trim();
        } catch (Exception e) {
            return "VPS";
        }
    }

    private static boolean needsYoutubeWarp() {
        return YT_WARPOUT;
    }

    private static String detectArch() {
        String arch = System.getProperty("os.arch").toLowerCase();
        if (arch.contains("amd64") || arch.contains("x86_64")) return "amd64";
        if (arch.contains("aarch64") || arch.contains("arm64")) return "arm64";
        return "amd64";
    }

    private static boolean isValidPort(String port) {
        if (port == null || port.isBlank()) return false;
        try {
            int p = Integer.parseInt(port.trim());
            return p > 0 && p <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static int runCommand(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).start();
        return process.waitFor();
    }

    private static void log(String message) {
        if (SHOW_LOG) {
            System.out.println(message);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Map<String, String> loadDotEnv() {
        Map<String, String> map = new LinkedHashMap<>();
        Path envFile = Path.of(".env");
        if (Files.exists(envFile)) {
            try {
                List<String> lines = Files.readAllLines(envFile, StandardCharsets.UTF_8);
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int idx = line.indexOf('=');
                    if (idx > 0) {
                        map.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
                    }
                }
            } catch (IOException ignored) {}
        }
        return map;
    }

    private static String env(String key, String defaultValue) {
        String sysEnv = System.getenv(key);
        if (sysEnv != null && !sysEnv.isBlank()) return sysEnv;
        return DOT_ENV.getOrDefault(key, defaultValue);
    }

    private static boolean envBool(String key, boolean defaultValue) {
        String val = env(key, String.valueOf(defaultValue));
        return List.of("true", "1", "yes").contains(val.toLowerCase());
    }

    private static int envInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(env(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static Optional<String> findJsonString(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        if (m.find()) return Optional.of(m.group(1));
        return Optional.empty();
    }

    @SafeVarargs
    private static <T> List<T> listOf(T... elements) {
        return List.of(elements);
    }

    private static Map<String, Object> mapOf(Object... kvs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kvs.length; i += 2) {
            map.put((String) kvs[i], kvs[i + 1]);
        }
        return map;
    }

    private static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return "\"" + escapeJson((String) obj) + "\"";
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
        if (obj instanceof List<?>) {
            List<?> list = (List<?>) obj;
            return "[" + list.stream().map(App::toJson).collect(Collectors.joining(",")) + "]";
        }
        if (obj instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) obj;
            return "{" + map.entrySet().stream()
                    .map(e -> "\"" + escapeJson(e.getKey().toString()) + "\":" + toJson(e.getValue()))
                    .collect(Collectors.joining(",")) + "}";
        }
        return "\"" + escapeJson(obj.toString()) + "\"";
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
