package searchengine.config;

import lombok.Getter;

@Getter
public class Messages {
    public static final String UNAVAILABLE_MAIN_PAGE = "Ошибка индексации: главная страница сайта не доступна";
    public static final String INDEXING_ABORTED = "Индексация остановлена пользователем";
    public static final String NOT_CONTAIN_PAGE = "Данная страница находится за пределами сайтов, указанных в конфигурационном файле";
    public static final String UNAVAILABLE_PAGE = "Данная страница недоступна";
    public static final String ERROR_LOAD_LEMMATIZATOR = "Не удалось инициализировать лемматизатор";
    public static final String NOT_LEMMA = "В указанном запросе не удалось получить список лемм";
    public static final String SEARCH_EMPTY_QUERY = "Задан пустой поисковый запрос";
    public static final String SEARCH_NO_INDEXED = "Не выполнена индексация";
}
