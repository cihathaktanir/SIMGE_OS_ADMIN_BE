package com.simge.adminbackend.erp;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * Mikro ERP için salt-okunur temel repository (ADR D-104, D-122).
 *
 * <p>
 * Bu servis <i>ileride</i> ERP'ye sınırlı yazma yapacak. Yine de varsayılan
 * burada da salt okunurdur: {@code save} / {@code delete} bu arayüzde yok,
 * dolayısıyla yazmak <b>derleme hatası</b>.
 * </p>
 *
 * <p>
 * <b>Yazma geldiğinde ne yapılacak:</b> bu dosya değiştirilmez. Yazma gereken
 * tablo için ayrı, dar bir arayüz yazılır — yalnızca o tabloya, yalnızca
 * gereken alanları güncelleyen adlandırılmış sorgularla. Böylece "hangi
 * repository yazabiliyor" sorusunun cevabı grep'lenebilir kalır; buraya
 * {@code save} eklemek ise tüm ERP'yi tek hamlede yazılabilir yapardı.
 * </p>
 *
 * <p>
 * Buraya yazma metodu eklemeyin.
 * </p>
 */
@NoRepositoryBean
public interface ReadOnlyRepository<T, ID> extends Repository<T, ID> {

    Optional<T> findById(ID id);

    List<T> findAll();

    boolean existsById(ID id);

    long count();
}
