using System.ComponentModel.DataAnnotations.Schema;

namespace Pixous.HrPortal.Domain.Common;

/// <summary>
/// Identity and audit columns, matching the Java <c>@MappedSuperclass</c>.
///
/// <para>Column names are pinned rather than left to a convention: the tables
/// already exist and are named by 153 Flyway migrations, so a naming policy
/// that disagreed would fail at startup — which is the correct outcome, but a
/// slow way to find out. <c>created_by</c> and <c>created_at</c> are never
/// updated, as the Java <c>updatable = false</c> said.</para>
/// </summary>
public abstract class BaseEntity
{
    [Column("id")]
    public long Id { get; set; }

    [Column("created_by")]
    public string? CreatedBy { get; set; }

    [Column("updated_by")]
    public string? UpdatedBy { get; set; }

    [Column("created_at")]
    public DateTime? CreatedAt { get; set; }

    [Column("updated_at")]
    public DateTime? UpdatedAt { get; set; }
}

/// <summary>
/// An entity that belongs to one company.
///
/// <para>Hibernate applied a <c>@Filter</c> that the session switched on per
/// request; EF Core's equivalent is a global query filter, configured in
/// <c>HrPortalDbContext</c> against the signed-in user's company. The rule is
/// the same and so is its one deliberate hole: rows with a null
/// <c>company_id</c> are visible to everybody, which is how holidays and the
/// seeded reference data are shared.</para>
///
/// <para>The Java <c>@PrePersist</c> stamped the company from the principal on
/// insert. That moves to <c>SaveChanges</c> in the context, for the same
/// reason: an entity created without one must not be written company-less.</para>
/// </summary>
public abstract class TenantEntity : BaseEntity
{
    [Column("company_id")]
    public long? CompanyId { get; set; }
}
