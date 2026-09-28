package ru.rtumirea.repairly.repository;
import java.util.List;
public interface BaseRepository<T> {
    T save(T entity);
    List<T> findAll();
    T findById(int id);
    boolean deleteById(int id);
}
