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

    public long? UserId =>
        long.TryParse(Principal?.FindFirst("uid")?.Value, out var id) ? id : null;

    public string? Username => Principal?.FindFirst(ClaimTypes.Name)?.Value;

    public string? EmployeeCode => Principal?.FindFirst("employeeCode")?.Value;

    public long? CompanyId =>
        long.TryParse(Principal?.FindFirst("companyId")?.Value, out var id) ? id : null;

    public IReadOnlySet<string> Roles =>
        Principal?.FindAll("roles").Select(c => c.Value).ToHashSet() ?? [];

    public IReadOnlySet<string> Permissions =>
        Principal?.FindAll("permissions").Select(c => c.Value).ToHashSet() ?? [];

    /// <summary>
    /// Whether the caller holds an authority, matching Spring's
    /// <c>hasAuthority</c> — which does not distinguish a role from a
    /// permission, so neither does this.
    /// </summary>
    public bool HasAuthority(string authority) =>
        Permissions.Contains(authority) || Roles.Contains(authority);
}
