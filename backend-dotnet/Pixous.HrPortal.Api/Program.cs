using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.AspNetCore.HttpOverrides;
using Pixous.HrPortal.Api.Middleware;
using Pixous.HrPortal.Api.RealTime;
using Pixous.HrPortal.Infrastructure;

/*
 * The ASP.NET Core host, standing in for Spring Boot's auto-configuration.
 *
 * Ordering below is not cosmetic. Forwarded headers must run before anything
 * that reads the scheme or the client address, because behind IIS every request
 * arrives over plain HTTP on the loopback and would otherwise look insecure and
 * come from 127.0.0.1 -- which would break the login throttle, the audit trail
 * and any redirect to https. CORS must precede authentication so a rejected
 * pre-flight still carries the headers a browser needs to read the rejection.
 * The exception middleware wraps everything, so a failure inside authentication
 * comes back in the same envelope as one inside a controller.
 */

var builder = WebApplication.CreateBuilder(args);

/*
 * IIS.
 *
 * The production target is Windows Server behind IIS, in-process. Out-of-process
 * would work too and is slower for every request; in-process needs the module to
 * own the port, which is what UseIISIntegration arranges. Kestrel stays for
 * local runs and for the container.
 */
builder.WebHost.UseIISIntegration();
builder.Services.Configure<IISServerOptions>(o =>
{
    // Matches spring.servlet.multipart.max-request-size=25MB. IIS has its own
    // limit in web.config as well; both have to allow it.
    o.MaxRequestBodySize = 25 * 1024 * 1024;
});

builder.Services.Configure<ForwardedHeadersOptions>(o =>
{
    o.ForwardedHeaders = ForwardedHeaders.XForwardedFor | ForwardedHeaders.XForwardedProto;
    /*
     * Cleared deliberately. The defaults trust only loopback, which is right
     * for IIS on the same box and wrong for the Docker deployment, where the
     * proxy is on the bridge network. Both hosts put a trusted proxy directly
     * in front of the app, so the header is trustworthy in each; leaving the
     * known-network list empty accepts it from either.
     */
    o.KnownNetworks.Clear();
    o.KnownProxies.Clear();
});

builder.Services.AddControllers()
    .AddJsonOptions(o =>
    {
        /*
         * Jackson's defaults, reproduced.
         *
         * Java's ObjectMapper omits nulls on the response envelope and writes
         * property names exactly as declared -- the records spell them in
         * camelCase and Jackson leaves them alone. System.Text.Json defaults to
         * camelCase conversion, which happens to agree, but every DTO here
         * carries an explicit [JsonPropertyName] so agreement is not the thing
         * holding the contract together.
         */
        o.JsonSerializerOptions.DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull;
        o.JsonSerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.CamelCase;
        // Instant serialised as ISO-8601 UTC, which is what the client parses.
        o.JsonSerializerOptions.NumberHandling = JsonNumberHandling.AllowReadingFromString;
    });

builder.Services.AddHrPortalInfrastructure(builder.Configuration);

/*
 * Real time: the STOMP-over-SockJS endpoint the existing React client already
 * speaks to. See REALTIME.md for why this rather than SignalR -- eight files,
 * nine destinations, and a client that never publishes.
 *
 * The registry is a singleton because it holds the open sockets, which is what
 * Spring's enableSimpleBroker did in-process too.
 */
builder.Services.AddSingleton<StompRegistry>();
builder.Services.AddSingleton<IStompPublisher>(sp => sp.GetRequiredService<StompRegistry>());
builder.Services.AddSingleton<IStompAuthorizer, StompAuthorizer>();

/*
 * CORS, matching WebConfig.corsConfigurationSource exactly: the configured
 * origins, six methods, any header, Authorization exposed, credentials allowed,
 * one hour of pre-flight cache.
 */
const string CorsPolicy = "hr-portal";
var allowedOrigins = builder.Configuration
    .GetSection("App:Cors:AllowedOrigins").Get<string[]>()
    ?? ["http://localhost:5174", "http://localhost:3000"];

builder.Services.AddCors(o => o.AddPolicy(CorsPolicy, p => p
    .WithOrigins(allowedOrigins)
    .WithMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
    .AllowAnyHeader()
    .WithExposedHeaders("Authorization")
    .AllowCredentials()
    .SetPreflightMaxAge(TimeSpan.FromHours(1))));

var app = builder.Build();

app.UseForwardedHeaders();

/*
 * WebSockets, before routing so the upgrade is available to the endpoint.
 * KeepAliveInterval matches the SockJS heart-beat the endpoint sends, so a
 * proxy sees traffic from both layers rather than relying on either alone.
 */
app.UseWebSockets(new WebSocketOptions { KeepAliveInterval = TimeSpan.FromSeconds(30) });

app.UseMiddleware<ExceptionMiddleware>();
app.UseCors(CorsPolicy);
app.UseAuthentication();
app.UseAuthorization();
app.MapControllers();
app.MapStomp();

/*
 * The health endpoint Spring Boot Actuator exposed at /actuator/health, at the
 * same path so the uptime monitor and the container health check do not change.
 * Redis and mail are deliberately not part of it, for the reason recorded in
 * application.properties: both are optional at runtime, and reporting DOWN when
 * they are absent made hosts restart a healthy app.
 */
app.MapGet("/actuator/health", () => Results.Json(new { status = "UP" }));

/*
 * A push, for proving the real-time path end to end during the migration.
 *
 * Development only -- it is behind app.Environment.IsDevelopment() and is not
 * in the Java API, so it must never reach production. It exists because the
 * alternative way to test a push is to drive a whole module through to the
 * point where it publishes, and the socket needs proving before any module is
 * written.
 */
if (app.Environment.IsDevelopment())
{
    app.MapPost("/__dev/publish", async (
        string destination, string? user,
        IStompPublisher publisher, HttpContext ctx) =>
    {
        using var reader = new StreamReader(ctx.Request.Body);
        string body = await reader.ReadToEndAsync();
        if (string.IsNullOrWhiteSpace(body)) body = "{}";

        if (user is null) await publisher.PublishAsync(destination, body);
        else await publisher.PublishToUserAsync(user, destination, body);

        return Results.Ok(new { published = destination, user });
    });
}

app.Run();
