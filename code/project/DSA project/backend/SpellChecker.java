package backend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpellChecker {

    // Maximum preferred edit distance
    static final int MAX_DISTANCE = 3;

    // Maximum number of suggestions
    static final int MAX_SUGGESTIONS = 5;

    // Dictionaries
    static Set<String> englishWords = new HashSet<>();
    static Set<String> hindiWords = new HashSet<>();
    static Set<String> teluguWords = new HashSet<>();


    // =========================================================
    // SUGGESTION CLASS
    // =========================================================

    static class Suggestion {

        String word;
        int distance;

        Suggestion(String word, int distance) {
            this.word = word;
            this.distance = distance;
        }
    }


    // =========================================================
    // ERROR WORD CLASS
    // =========================================================

    static class ErrorWord {

        String word;
        int start;
        int end;

        List<Suggestion> suggestions;

        ErrorWord(
                String word,
                int start,
                int end,
                List<Suggestion> suggestions) {

            this.word = word;
            this.start = start;
            this.end = end;
            this.suggestions = suggestions;
        }
    }


    // =========================================================
    // MAIN
    // =========================================================

    public static void main(String[] args) throws Exception {

        // Load dictionaries
        loadDictionary(
                "backend/dictionary_en.txt",
                englishWords
        );

        loadDictionary(
                "backend/dictionary_hi.txt",
                hindiWords
        );

        loadDictionary(
                "backend/dictionary_te.txt",
                teluguWords
        );


        System.out.println();
        System.out.println("======================================");
        System.out.println("   Intelligent Spell Checker");
        System.out.println("======================================");

        System.out.println(
                "English words : "
                        + englishWords.size()
        );

        System.out.println(
                "Hindi words   : "
                        + hindiWords.size()
        );

        System.out.println(
                "Telugu words  : "
                        + teluguWords.size()
        );

        System.out.println(
                "======================================"
        );


        // Create server
        HttpServer server =
                HttpServer.create(
                        new InetSocketAddress(8080),
                        0
                );


        // Main context
        server.createContext(
                "/",
                SpellChecker::handleRequest
        );


        server.setExecutor(null);

        server.start();


        System.out.println(
                "Server running at:"
        );

        System.out.println(
                "http://localhost:8080"
        );

        System.out.println(
                "======================================"
        );
    }


    // =========================================================
    // LOAD DICTIONARY
    // =========================================================

    static void loadDictionary(
            String fileName,
            Set<String> dictionary) {

        Path path =
                Paths.get(fileName);


        if (!Files.exists(path)) {

            System.out.println(
                    "Dictionary not found: "
                            + fileName
            );

            return;
        }


        try {

            List<String> lines =
                    Files.readAllLines(
                            path,
                            StandardCharsets.UTF_8
                    );


            for (String line : lines) {

                String word =
                        line.trim().toLowerCase();


                if (!word.isEmpty()) {

                    dictionary.add(word);
                }
            }


            System.out.println(
                    "Loaded: "
                            + fileName
            );


        } catch (IOException e) {

            System.out.println(
                    "Error loading: "
                            + fileName
            );
        }
    }


    // =========================================================
    // HANDLE REQUEST
    // =========================================================

    static void handleRequest(
            HttpExchange exchange)
            throws IOException {

        String path =
                exchange
                        .getRequestURI()
                        .getPath();


        // Spell checking API
        if (path.equals("/check")) {

            handleCheck(exchange);

            return;
        }


        // Frontend
        serveFrontend(
                exchange,
                path
        );
    }


    // =========================================================
    // SERVE FRONTEND
    // =========================================================

    static void serveFrontend(
            HttpExchange exchange,
            String path)
            throws IOException {


        if (path.equals("/")
                || path.isEmpty()) {

            path =
                    "/frontend/index.html";

        } else {

            path =
                    "/frontend" + path;
        }


        Path file =
                Paths.get("." + path);


        if (!Files.exists(file)) {

            sendResponse(
                    exchange,
                    404,
                    "File not found"
            );

            return;
        }


        byte[] data =
                Files.readAllBytes(file);


        String contentType =
                "text/html";


        if (path.endsWith(".css")) {

            contentType =
                    "text/css";

        } else if (path.endsWith(".js")) {

            contentType =
                    "application/javascript";
        }


        exchange
                .getResponseHeaders()
                .set(
                        "Content-Type",
                        contentType
                                + "; charset=UTF-8"
                );


        exchange.sendResponseHeaders(
                200,
                data.length
        );


        OutputStream output =
                exchange.getResponseBody();


        output.write(data);

        output.close();
    }


    // =========================================================
    // HANDLE SPELL CHECK
    // =========================================================

    static void handleCheck(
            HttpExchange exchange)
            throws IOException {


        String query =
                exchange
                        .getRequestURI()
                        .getRawQuery();


        Map<String, String> params =
                parseQuery(query);


        String text =
                params.getOrDefault(
                        "text",
                        ""
                );


        String language =
                params.getOrDefault(
                        "lang",
                        "en"
                );


        Set<String> dictionary =
                getDictionary(language);


        List<ErrorWord> errors =
                checkText(
                        text,
                        dictionary
                );


        String json =
                createJson(errors);


        byte[] response =
                json.getBytes(
                        StandardCharsets.UTF_8
                );


        exchange
                .getResponseHeaders()
                .set(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                );


        exchange.sendResponseHeaders(
                200,
                response.length
        );


        OutputStream output =
                exchange.getResponseBody();


        output.write(response);

        output.close();
    }


    // =========================================================
    // SELECT DICTIONARY
    // =========================================================

    static Set<String> getDictionary(
            String language) {


        if (language.equals("hi")) {

            return hindiWords;
        }


        if (language.equals("te")) {

            return teluguWords;
        }


        return englishWords;
    }


    // =========================================================
    // CHECK TEXT
    // =========================================================

    static List<ErrorWord> checkText(
            String text,
            Set<String> dictionary) {


        List<ErrorWord> errors =
                new ArrayList<>();


        /*
         * Unicode word pattern.
         *
         * Works for:
         * English
         * Hindi
         * Telugu
         * Other Unicode scripts
         */

        Pattern pattern =
                Pattern.compile(
                        "[\\p{L}\\p{M}]+(?:['’][\\p{L}\\p{M}]+)*"
                );


        Matcher matcher =
                pattern.matcher(text);


        while (matcher.find()) {


            String original =
                    matcher.group();


            String word =
                    original.toLowerCase();


            /*
             * If word is not present
             * in dictionary, it is an error.
             */

            if (!dictionary.contains(word)) {


                List<Suggestion> suggestions =
                        findSuggestions(
                                word,
                                dictionary
                        );


                errors.add(
                        new ErrorWord(
                                original,
                                matcher.start(),
                                matcher.end(),
                                suggestions
                        )
                );
            }
        }


        return errors;
    }


    // =========================================================
    // FIND SUGGESTIONS
    // =========================================================

    static List<Suggestion> findSuggestions(
            String word,
            Set<String> dictionary) {


        List<Suggestion> allSuggestions =
                new ArrayList<>();


        String input =
                word.trim().toLowerCase();


        /*
         * Compare the entered word
         * with every dictionary word.
         */

        for (String candidate : dictionary) {


            if (candidate == null
                    || candidate.isEmpty()) {

                continue;
            }


            String cleanCandidate =
                    candidate
                            .trim()
                            .toLowerCase();


            /*
             * Calculate Levenshtein distance.
             */

            int distance =
                    levenshteinDistance(
                            input,
                            cleanCandidate
                    );


            allSuggestions.add(
                    new Suggestion(
                            cleanCandidate,
                            distance
                    )
            );
        }


        /*
         * Sort by:
         *
         * 1. Smallest distance
         * 2. Alphabetical order
         */

        allSuggestions.sort(
                Comparator
                        .<Suggestion>comparingInt(
                                s -> s.distance
                        )
                        .thenComparing(
                                s -> s.word
                        )
        );


        /*
         * First try to find words
         * within distance 3.
         */

        List<Suggestion> closeSuggestions =
                new ArrayList<>();


        for (Suggestion suggestion :
                allSuggestions) {


            if (suggestion.distance
                    <= MAX_DISTANCE) {


                closeSuggestions.add(
                        suggestion
                );


                if (closeSuggestions.size()
                        >= MAX_SUGGESTIONS) {

                    break;
                }
            }
        }


        /*
         * If close suggestions exist,
         * return them.
         */

        if (!closeSuggestions.isEmpty()) {

            return closeSuggestions;
        }


        /*
         * IMPORTANT:
         *
         * If there is no suggestion
         * within distance 3, we still
         * return the closest words.
         *
         * This helps Hindi and Telugu
         * with smaller dictionaries.
         */

        if (allSuggestions.size()
                > MAX_SUGGESTIONS) {

            return new ArrayList<>(
                    allSuggestions.subList(
                            0,
                            MAX_SUGGESTIONS
                    )
            );
        }


        return allSuggestions;
    }


    // =========================================================
    // LEVENSHTEIN EDIT DISTANCE
    // =========================================================

    static int levenshteinDistance(
            String a,
            String b) {


        /*
         * Convert Unicode strings
         * into code points.
         *
         * This supports Hindi/Telugu
         * characters properly.
         */

        int[] first =
                a.codePoints().toArray();


        int[] second =
                b.codePoints().toArray();


        int m =
                first.length;


        int n =
                second.length;


        /*
         * Dynamic Programming table.
         */

        int[][] dp =
                new int[m + 1][n + 1];


        /*
         * First column.
         */

        for (int i = 0;
             i <= m;
             i++) {

            dp[i][0] = i;
        }


        /*
         * First row.
         */

        for (int j = 0;
             j <= n;
             j++) {

            dp[0][j] = j;
        }


        /*
         * Fill DP table.
         */

        for (int i = 1;
             i <= m;
             i++) {


            for (int j = 1;
                 j <= n;
                 j++) {


                int cost;


                if (first[i - 1]
                        == second[j - 1]) {

                    cost = 0;

                } else {

                    cost = 1;
                }


                /*
                 * Minimum of:
                 *
                 * Insertion
                 * Deletion
                 * Substitution
                 */

                dp[i][j] =
                        Math.min(

                                Math.min(
                                        dp[i - 1][j]
                                                + 1,

                                        dp[i][j - 1]
                                                + 1
                                ),

                                dp[i - 1][j - 1]
                                        + cost
                        );
            }
        }


        return dp[m][n];
    }


    // =========================================================
    // PARSE URL QUERY
    // =========================================================

    static Map<String, String> parseQuery(
            String query) {


        Map<String, String> result =
                new HashMap<>();


        if (query == null) {

            return result;
        }


        for (String part :
                query.split("&")) {


            String[] pieces =
                    part.split("=", 2);


            if (pieces.length == 2) {


                String key =
                        URLDecoder.decode(
                                pieces[0],
                                StandardCharsets.UTF_8
                        );


                String value =
                        URLDecoder.decode(
                                pieces[1],
                                StandardCharsets.UTF_8
                        );


                result.put(
                        key,
                        value
                );
            }
        }


        return result;
    }


    // =========================================================
    // CREATE JSON RESPONSE
    // =========================================================

    static String createJson(
            List<ErrorWord> errors) {


        StringBuilder json =
                new StringBuilder();


        json.append(
                "{\"errors\":["
        );


        for (int i = 0;
             i < errors.size();
             i++) {


            ErrorWord error =
                    errors.get(i);


            if (i > 0) {

                json.append(",");
            }


            json.append("{");


            json.append(
                    "\"word\":\""
            );


            json.append(
                    jsonEscape(
                            error.word
                    )
            );


            json.append("\",");


            json.append(
                    "\"start\":"
            );


            json.append(
                    error.start
            );


            json.append(",");


            json.append(
                    "\"end\":"
            );


            json.append(
                    error.end
            );


            json.append(",");


            json.append(
                    "\"suggestions\":["
            );


            for (int j = 0;
                 j < error.suggestions.size();
                 j++) {


                Suggestion suggestion =
                        error.suggestions.get(j);


                if (j > 0) {

                    json.append(",");
                }


                json.append("{");


                json.append(
                        "\"word\":\""
                );


                json.append(
                        jsonEscape(
                                suggestion.word
                        )
                );


                json.append("\",");


                json.append(
                        "\"distance\":"
                );


                json.append(
                        suggestion.distance
                );


                json.append("}");
            }


            json.append("]");


            json.append("}");
        }


        json.append("]}");


        return json.toString();
    }


    // =========================================================
    // ESCAPE JSON
    // =========================================================

    static String jsonEscape(
            String text) {


        return text

                .replace(
                        "\\",
                        "\\\\"
                )

                .replace(
                        "\"",
                        "\\\""
                )

                .replace(
                        "\n",
                        "\\n"
                )

                .replace(
                        "\r",
                        "\\r"
                )

                .replace(
                        "\t",
                        "\\t"
                );
    }


    // =========================================================
    // SEND RESPONSE
    // =========================================================

    static void sendResponse(
            HttpExchange exchange,
            int status,
            String text)
            throws IOException {


        byte[] data =
                text.getBytes(
                        StandardCharsets.UTF_8
                );


        exchange
                .getResponseHeaders()
                .set(
                        "Content-Type",
                        "text/plain; charset=UTF-8"
                );


        exchange.sendResponseHeaders(
                status,
                data.length
        );


        OutputStream output =
                exchange.getResponseBody();


        output.write(data);

        output.close();
    }
}