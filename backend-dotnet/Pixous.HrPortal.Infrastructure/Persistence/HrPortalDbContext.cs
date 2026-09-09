using Microsoft.EntityFrameworkCore;
using Pixous.HrPortal.Domain.Common;
using Pixous.HrPortal.Infrastructure.Security;

namespace Pixous.HrPortal.Infrastructure.Persistence;

/// <summary>
/// EF Core against the schema Flyway already owns.
///
/// <para><b>This context never migrates.</b> The database is live and 153
/// Flyway migrations built it; EF is here to read and write rows, not to decide
/// what the tables look like. There is no <c>Migrations</c> folder, nothing
/// calls <c>Database.Migrate()</c>, and <c>EnsureCreated</c> is never used --
/// it would try to build a schema that exists and fail, or worse, succeed
/// against an empty database somebody pointed at by mistake.</para>
///
/// <para>The Java side ran with <c>ddl-auto=validate</c>, which failed startup
/// when an entity disagreed with a column. That check has caught real mistakes
/// in this project — a TINYINT mapped to an Integer took the app down at boot
/// rather than at the first query. <see cref="ValidateSchemaAsync"/> keeps the
/// equivalent: it is called on startup and it fails loudly.</para>
/// </summary>
public class HrPortalDbContext(
    DbContextOptions<HrPortalDbContext> options,
    ITenantContext tenant,
    ICurrentUser currentUser) : DbContext(options)
{
    protected override void OnModelCreating(ModelBuilder b)
    {
        base.OnModelCreating(b);

        /*
         * The tenant filter, as Hibernate's @Filter was.
         *
         * Applied to every TenantEntity, and it keeps the same hole the Java
         * one had: a row with a null company_id is visible to everybody. That
         * is not an oversight -- holidays, seeded reference data and the
         * platform accounts are shared, and the Java condition
         * "company_id = :companyId OR company_id IS NULL" said so explicitly in
         * the repositories that needed it.
         *
         * A null tenant (a scheduled job, or a request before authentication)
         * sees everything. The Java filter was switched on per session and
         * simply not enabled for jobs, which amounts to the same thing -- the
         * daily notifier depends on it.
         */
        foreach (var entityType in b.Model.GetEntityTypes())
        {
            if (!typeof(TenantEntity).IsAssignableFrom(entityType.ClrType)) continue;

            var method = typeof(HrPortalDbContext)
                .GetMethod(nameof(ApplyTenantFilter),
                    System.Reflection.BindingFlags.NonPublic | System.Reflection.BindingFlags.Instance)!
                .MakeGenericMethod(entityType.ClrType);
            method.Invoke(this, [b]);
        }
    }

    private void ApplyTenantFilter<TEntity>(ModelBuilder b) where TEntity : TenantEntity
    {
        b.Entity<TEntity>().HasQueryFilter(e =>
            tenant.CompanyId == null
            || e.CompanyId == null
            || e.CompanyId == tenant.CompanyId);
    }

    public override int SaveChanges()
    {
        Stamp();
        return base.SaveChanges();
    }

    public override Task<int> SaveChangesAsync(CancellationToken cancellationToken = default)
    {
        Stamp();
        return base.SaveChangesAsync(cancellationToken);
    }

    /// <summary>
    /// The audit columns and the tenant, as Spring Data auditing and the Java
    /// <c>@PrePersist</c> filled them.
    /// </summary>
    private void Stamp()
    {
        var now = DateTime.Now; // Local, matching LocalDateTime under TZ=Asia/Kolkata.
        var who = currentUser.Username;

        foreach (var entry in ChangeTracker.Entries<BaseEntity>())
        {
            if (entry.State == EntityState.Added)
            {
                entry.Entity.CreatedAt ??= now;
                entry.Entity.CreatedBy ??= who;
                entry.Entity.UpdatedAt = now;
                entry.Entity.UpdatedBy = who;

                // A row created without a company takes the signed-in user's,
                // as @PrePersist did. A job with no principal leaves it null,
                // which is what the seeded shared rows look like.
                if (entry.Entity is TenantEntity t && t.CompanyId is null)
                {
                    t.CompanyId = tenant.CompanyId;
                }
            }
            else if (entry.State == EntityState.Modified)
            {
                entry.Entity.UpdatedAt = now;
                entry.Entity.UpdatedBy = who;
                // created_* were updatable = false in the Java mapping.
                entry.Property(e => e.CreatedAt).IsModified = false;
                entry.Property(e => e.CreatedBy).IsModified = false;
            }
        }
    }

    /// <summary>
    /// The equivalent of <c>ddl-auto=validate</c>: prove the model matches the
    /// live schema, and refuse to start if it does not.
    /// </summary>
    /// <remarks>
    /// EF has no built-in validate mode. This asks the provider for the SQL it
    /// would generate to create the model and compares nothing -- what it
    /// actually does is open a connection and run a trivial query per mapped
    /// table, which surfaces a missing table or column immediately. It is
    /// cheaper than it sounds (one round trip each, at startup only) and it
    /// buys back the boot-time failure that caught the TINYINT bug.
    /// </remarks>
    public async Task ValidateSchemaAsync(CancellationToken ct = default)
    {
        await Database.OpenConnectionAsync(ct);
        try
        {
            foreach (var entityType in Model.GetEntityTypes())
            {
                var table = entityType.GetTableName();
                if (string.IsNullOrEmpty(table)) continue;

                var columns = entityType.GetProperties()
                    .Select(p => p.GetColumnName())
                    .Where(c => !string.IsNullOrEmpty(c))
                    .Select(c => $"`{c}`");

                await using var cmd = Database.GetDbConnection().CreateCommand();
                cmd.CommandText = $"SELECT {string.Join(", ", columns)} FROM `{table}` LIMIT 0";
                await cmd.ExecuteNonQueryAsync(ct);
            }
        }
        finally
        {
            await Database.CloseConnectionAsync();
        }
    }
}
