package com.swyp.backend.terms.repository;

import com.swyp.backend.terms.entity.TermsDocument;
import com.swyp.backend.user.entity.UserRole;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TermsDocumentRepository extends JpaRepository<TermsDocument, Long> {

	@Query("""
			select d from TermsDocument d
			where d.role = :role
				and d.version = (
					select max(latest.version) from TermsDocument latest
					where latest.role = d.role and latest.type = d.type)
			""")
	List<TermsDocument> findCurrentByRole(@Param("role") UserRole role);
}
