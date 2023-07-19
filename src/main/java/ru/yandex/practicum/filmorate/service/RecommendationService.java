package ru.yandex.practicum.filmorate.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Like;
import ru.yandex.practicum.filmorate.storage.Dao.LikeDao;
import ru.yandex.practicum.filmorate.storage.FilmStorage;
import ru.yandex.practicum.filmorate.storage.UserStorage;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationService {
    private final FilmStorage filmStorage;
    private final LikeDao likeDao;
    private final UserStorage userStorage;

    public List<Film> getRecommendedFilms(Integer id) {
        userStorage.userExistenceCheck(id);
        // получаем список всех лайков
        List<Like> allLikes = likeDao.getAllLikes();
        // получаем список id всех пользователей и список id фильмов, который они пролайкали
        Map<Integer, List<Integer>> usersAndLikedFilmsIds = makeMapOfUsersAndLikes(allLikes);
        // получаем список рекомендаций для пользователя
        List<Integer> listOfRecommendedFilmsIds = makeRecommendationsList(usersAndLikedFilmsIds, id);

        if (listOfRecommendedFilmsIds.size() < 1) {
            return Collections.emptyList();
        }
        // получаем список рекомендованных фильмов
        return listOfRecommendedFilmsIds.stream()
                .map(filmStorage::getFilmId)
                .collect(Collectors.toList());
    }

    private List<Integer> makeRecommendationsList(
            Map<Integer, List<Integer>> usersAndLikedFilmsIds,
            int userId) {
        // создаем и заполняем список id пользователей и количества совпадений по лайкам с нашим пользователем.
        List<Integer> userLikedFilmsIds = usersAndLikedFilmsIds.get(userId);
        Map<Integer, Long> idsAndMatchesCount = new HashMap<>();

        for (int otherUserId : usersAndLikedFilmsIds.keySet()) {
            if (otherUserId == userId) {
                continue;
            }
            List<Integer> userLikes = new ArrayList<>(userLikedFilmsIds);
            userLikes.retainAll(usersAndLikedFilmsIds.get(otherUserId));
            if (userLikes.isEmpty()) {
                continue;
            }
            long commonLikesCount = userLikes.size();
            idsAndMatchesCount.put(otherUserId, commonLikesCount);
        }

        if (idsAndMatchesCount.isEmpty()) {
            return Collections.emptyList();
        }
        // Найти топ 10 пользователей с максимальным количеством пересечения по лайкам.
        List<Integer> topTenMatchesUsersIds = idsAndMatchesCount.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).limit(10)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Определить id фильмов, которые наш пользователь не лайкал.
        return topTenMatchesUsersIds.stream()
                .map(usersAndLikedFilmsIds::get)
                .flatMap(List::stream)
                .filter(filmId -> !userLikedFilmsIds.contains(filmId))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private Map<Integer, List<Integer>> makeMapOfUsersAndLikes(List<Like> allLikes) {
        return allLikes.stream()
                .collect(Collectors.toMap(
                        Like::getUserId,
                        like -> List.of(like.getFilmId()),
                        this::concatenate
                ));
    }

    private <T> List<T> concatenate(List<T> first, List<T> second) {
        List<T> result = new ArrayList<>(first);
        result.addAll(second);
        return result;
    }
}
