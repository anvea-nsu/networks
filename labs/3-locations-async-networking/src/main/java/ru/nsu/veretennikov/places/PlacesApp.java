package ru.nsu.veretennikov.places;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.json.*;

public class PlacesApp {
    private static final String GRAPHHOPPER_KEY = "8f487e46-cdd4-4b38-84f8-1c98f74a9cf3";
    private static final String OPENWEATHER_KEY = "b7a1a150b83547778248e3acfad617a4";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    static class Location {
        String name;
        double lat;
        double lon;
        String country;

        @Override
        public String toString() {
            return name + " (" + country + ")";
        }
    }

    static class Weather {
        String description;
        double temp;
        int humidity;
        double windSpeed;

        @Override
        public String toString() {
            return String.format("Погода: %s, Температура: %.1f°C, Влажность: %d%%, Ветер: %.1f м/с",
                    description, temp, humidity, windSpeed);
        }
    }

    static class Place {
        String title;
        String description;

        @Override
        public String toString() {
            return String.format("\n  - %s\n    %s",
                    title, description);
        }
    }

    static class Result {
        Location location;
        Weather weather;
        List<Place> places;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("\n========================================\n");
            sb.append("РЕЗУЛЬТАТЫ ПОИСКА\n");
            sb.append("========================================\n");
            sb.append("Локация: ").append(location).append("\n");
            sb.append("Координаты: ").append(location.lat).append(", ").append(location.lon).append("\n\n");
            sb.append(weather).append("\n\n");
            sb.append("Интересные места (").append(places.size()).append("):\n");
            places.forEach(p -> sb.append(p));
            sb.append("\n========================================\n");
            return sb.toString();
        }
    }

    // [1] GraphHopper Geocoding API
    private CompletableFuture<List<Location>> searchLocations(String query) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = String.format(
                    "https://graphhopper.com/api/1/geocode?q=%s&key=%s&limit=10",
                    encodedQuery, GRAPHHOPPER_KEY
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        List<Location> locations = new ArrayList<>();
                        JSONObject json = new JSONObject(response.body());
                        JSONArray hits = json.getJSONArray("hits");

                        for (int i = 0; i < hits.length(); i++) {
                            JSONObject hit = hits.getJSONObject(i);
                            Location loc = new Location();
                            loc.name = hit.optString("name", "Unknown");
                            JSONObject point = hit.getJSONObject("point");
                            loc.lat = point.getDouble("lat");
                            loc.lon = point.getDouble("lng");
                            loc.country = hit.optString("country", "Unknown");
                            locations.add(loc);
                        }
                        return locations;
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    // [2] OpenWeatherMap API
    private CompletableFuture<Weather> getWeather(double lat, double lon) {
        String url = String.format(
                "https://api.openweathermap.org/data/2.5/weather?lat=%f&lon=%f&appid=%s&units=metric&lang=ru",
                lat, lon, OPENWEATHER_KEY
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    JSONObject json = new JSONObject(response.body());
                    Weather weather = new Weather();
                    weather.description = json.getJSONArray("weather")
                            .getJSONObject(0).getString("description");
                    weather.temp = json.getJSONObject("main").getDouble("temp");
                    weather.humidity = json.getJSONObject("main").getInt("humidity");
                    weather.windSpeed = json.getJSONObject("wind").getDouble("speed");
                    return weather;
                });
    }

    // [3] Wikipedia Geosearch API
    private CompletableFuture<List<String>> searchNearbyPlaces(double lat, double lon) {
        try {
            String url = String.format(
                    Locale.US,
                    "https://ru.wikipedia.org/w/api.php?action=query&list=geosearch&gscoord=%.6f%%7C%.6f&gsradius=10000&gslimit=10&format=json",
                    lat, lon
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "PlacesApp/1.0")
                    .GET()
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        String body = response.body();
                        System.out.println("Wikipedia Geosearch response: " + body.substring(0, Math.min(200, body.length())));

                        List<String> pageIds = new ArrayList<>();
                        JSONObject json = new JSONObject(body);

                        if (!json.has("query")) {
                            System.out.println("Warning: No 'query' field in response");
                            return pageIds;
                        }

                        JSONArray results = json.getJSONObject("query").getJSONArray("geosearch");

                        for (int i = 0; i < results.length(); i++) {
                            JSONObject item = results.getJSONObject(i);
                            pageIds.add(String.valueOf(item.getInt("pageid")));
                        }
                        return pageIds;
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    // [4] Wikipedia API
    private CompletableFuture<Place> getPlaceDescription(String pageId, double originLat, double originLon) {
        try {
            String url = String.format(
                    "https://ru.wikipedia.org/w/api.php?action=query&pageids=%s&prop=extracts%%7Ccoordinates&exintro=1&explaintext=1&format=json",
                    pageId
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "PlacesApp/1.0")
                    .GET()
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        JSONObject json = new JSONObject(response.body());
                        JSONObject pages = json.getJSONObject("query").getJSONObject("pages");
                        JSONObject page = pages.getJSONObject(pageId);

                        Place place = new Place();
                        place.title = page.optString("title", "Без названия");

                        String extract = page.optString("extract", "Описание отсутствует");
                        place.description = extract.length() > 500
                                ? extract.substring(0, 500) + "..."
                                : extract;

                        return place;
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<List<Location>> findLocations(String query) {
        return searchLocations(query);
    }

    public CompletableFuture<Result> getLocationInfo(Location location) {
        CompletableFuture<Weather> weatherFuture = getWeather(location.lat, location.lon);
        CompletableFuture<List<String>> placesFuture = searchNearbyPlaces(location.lat, location.lon);

        CompletableFuture<List<Place>> placesWithDescriptions = placesFuture
                .thenCompose(pageIds -> {
                    List<CompletableFuture<Place>> placeFutures = pageIds.stream()
                            .map(pageId -> getPlaceDescription(pageId, location.lat, location.lon))
                            .collect(Collectors.toList());

                    return CompletableFuture.allOf(placeFutures.toArray(new CompletableFuture[0]))
                            .thenApply(v -> placeFutures.stream()
                                    .map(CompletableFuture::join)
                                    .collect(Collectors.toList()));
                });

        return weatherFuture.thenCombine(placesWithDescriptions, (weather, places) -> {
            Result result = new Result();
            result.location = location;
            result.weather = weather;
            result.places = places;
            return result;
        });
    }

    public static void main(String[] args) {
        PlacesApp app = new PlacesApp();
        Scanner scanner = new Scanner(System.in);

        try (scanner) {
            System.out.println("=== ПОИСК ИНТЕРЕСНЫХ МЕСТ ===\n");
            System.out.print("Введите название места: ");

            String query = scanner.nextLine();
            System.out.println("\nИдёт поиск локаций...");
            List<Location> locations = app.findLocations(query).join();

            if (locations.isEmpty()) {
                System.out.println("Локации не найдены.");
                return;
            }

            System.out.println("\nНайденные локации:");
            for (int i = 0; i < locations.size(); i++) {
                System.out.println((i + 1) + ". " + locations.get(i));
            }

            System.out.print("\nВыберите номер локации (1-" + locations.size() + "): ");
            int choice = scanner.nextInt() - 1;

            if (choice < 0 || choice >= locations.size()) {
                System.out.println("Неверный выбор.");
                return;
            }

            Location selectedLocation = locations.get(choice);

            System.out.println("\nЗагрузка информации...");
            Result result = app.getLocationInfo(selectedLocation).join();

            System.out.println(result);

        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }
}