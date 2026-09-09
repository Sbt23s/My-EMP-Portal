using System.Security.Claims;
using Microsoft.AspNetCore.Http;

namespace Pixous.HrPortal.Infrastructure.Security;

/// <summary>
/// Who is making this request, as <c>SecurityUtils</c> answered it in Java.
/// </summary>
/// <remarks>
/// The Java version read a thread-local <c>SecurityContextHolder</c> from
/// static methods, which is why it could be called from anywhere. .NET has no
/// equivalent that survives an <c>await</c> safely, so this is injected. Every
/// call site that said <c>SecurityUtils.currentUserId()</c> takes an
/// <c>ICurrentUser</c> instead.
/// </remarks>
public interface ICurrentUser
{
    long? UserId { get; }
    string? Username { get; }
    string? EmployeeCode { get; }
    IReadOnlySet<string> Roles { get; }
    IReadOnlySet<string> Permissions { get; }
    bool HasAuthority(string authority);
}

public interface ITenantContext
{
    long? CompanyId { get; }
}

public sealed class HttpCurrentUser(IHttpContextAccessor accessor) : ICurrentUser, ITenantContext
{
    private ClaimsPrincipal? Principal => accessor.HttpContext?.User;

    /*
     * The claim names are the Java token's, not .NET conventions.
     *
     * The token is issued and read by both backends during the migration -- a
     * browser signed in against Java must work against .NET without signing in
     * again -- so the shape is fixed by JwtService:
     *
     *   sub       the user id, as a string
     *   username  the login name
     *   roles     an array of role codes
     *   userType  USER or TECHNICAL_ADMIN
     *
     * There is no employeeCode and no companyId in the token. Both are looked
     * up from the user row, exactly as the Java UserPrincipal did -- putting
     * them in the token would mean a role change needed a fresh login, which
     * is a behaviour change.
     */
    public long? UserId =>
        long.TryParse(Principal?.FindFirst("sub")?.Value
                      ?? Principal?.FindFirst(ClaimTypes.NameIdentifier)?.Value,
                      out var id) ? id : null;

    public string? Username =>
        Principal?.FindFirst("username")?.Value
        ?? Principal?.FindFirst(ClaimTypes.Name)?.Value;

    /// <summary>
    /// Filled from the user row by the authentication handler, not from the
    /// token -- see the note above.
    /// </summary>
    public string? EmployeeCode => Principal?.FindFirst("employeeCode")?.Value;

    public long? CompanyId =>
        long.TryParse(Principal?.FindFirst("companyId")?.Value, out var id) ? id : null;

    public IReadOnlySet<string> Roles =>
        Principal?.FindAll("roles").Select(c => c.Value).ToHashSet(StringComparer.Ordinal) ?? [];

    public IReadOnlySet<string> Permissions =>
        Principal?.FindAll("permissions").Select(c => c.Value).ToHashSet(StringComparer.Ordinal) ?? [];

    /// <summary>
    /// Whether the caller holds an authority, matching Spring's
    /// <c>hasAuthority</c> — which does not distinguish a role from a
    /// permission, so neither does this.
    /// </summary>
    public bool HasAuthority(string authority) =>
        Permissions.Contains(authority) || Roles.Contains(authority);
}
