using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Pixous.HrPortal.Infrastructure.Persistence;
using Pixous.HrPortal.Infrastructure.Security;

namespace Pixous.HrPortal.Infrastructure;

public static class DependencyInjection
{
    /// <summary>
    /// Everything the API needs from the infrastructure layer: the database,
    /// the current-user accessor and the tenant context.
    /// </summary>
    public static IServiceCollection AddHrPortalInfrastructure(
        this IServiceCollection services, IConfiguration config)
    {
        services.AddHttpContextAccessor();

        /*
         * Registered once and exposed under both interfaces, so the tenant a
         * query filters by and the user an audit column records are the same
         * request's -- reading them from two objects is how they drift.
         */
        services.AddScoped<HttpCurrentUser>();
        services.AddScoped<ICurrentUser>(sp => sp.GetRequiredService<HttpCurrentUser>());
        services.AddScoped<ITenantContext>(sp => sp.GetRequiredService<HttpCurrentUser>());

        // Reads a token outside the authentication middleware -- the WebSocket
        // CONNECT frame needs it before that middleware has run.
        services.AddSingleton<Pixous.HrPortal.Domain.Security.IJwtReader, JwtReader>();

        var connection = config.GetConnectionString("HrPortal")
            ?? throw new InvalidOperationException(
                "ConnectionStrings:HrPortal is not set. The Java side took this from "
                + "DB_HOST/DB_NAME/DB_USER/DB_PASSWORD; see appsettings for the mapping.");

        services.AddDbContext<HrPortalDbContext>(o =>
        {
            o.UseMySql(connection, ServerVersion.AutoDetect(connection), my =>
            {
                /*
                 * Matched to what the Java side ran with:
                 * spring.jpa.properties.hibernate.jdbc.batch_size=50 and
                 * default_batch_fetch_size=50. The second one mattered -- roles
                 * are eager on User and permissions eager on Role, so loading
                 * six employees cost 112 queries before batching.
                 */
                my.MaxBatchSize(50);
                my.EnableRetryOnFailure(3);
            });

            /*
             * No tracking by default, as Hibernate's read-only transactions
             * effectively gave us for the many list endpoints. Anything that
             * writes opts back in explicitly, which makes the write visible in
             * the code rather than implicit in a session.
             */
            o.UseQueryTrackingBehavior(QueryTrackingBehavior.NoTracking);
        });

        return services;
    }
}
