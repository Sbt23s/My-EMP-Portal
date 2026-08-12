package com.pixous.hrportal.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.pixous.hrportal.security.UserPrincipal;

@Getter
@Setter
@MappedSuperclass
@FilterDef(name = "tenantFilter", parameters = {@ParamDef(name = "companyId", type = Long.class)})
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public abstract class TenantEntity extends BaseEntity {

    @Column(name = "company_id", updatable = false)
    private Long companyId;

    @PrePersist
    public void onPrePersistTenant() {
        if (this.companyId == null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UserPrincipal) {
                UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
                this.companyId = principal.getCompanyId();
            }
        }
    }
}
