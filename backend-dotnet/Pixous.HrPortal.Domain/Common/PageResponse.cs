using System.Text.Json.Serialization;

namespace Pixous.HrPortal.Domain.Common;

/// <summary>
/// The pagination wrapper, as the Java record serialised it.
///
/// <para>Zero-based <c>page</c>, matching Spring Data — the frontend's
/// <c>usePagedRows</c> and every <c>?page=0</c> in the client assume it, and an
/// off-by-one here would silently skip the first page everywhere.</para>
/// </summary>
public sealed record PageResponse<T>(
    [property: JsonPropertyName("content")] IReadOnlyList<T> Content,
    [property: JsonPropertyName("page")] int Page,
    [property: JsonPropertyName("size")] int Size,
    [property: JsonPropertyName("totalElements")] long TotalElements,
    [property: JsonPropertyName("totalPages")] int TotalPages,
    [property: JsonPropertyName("last")] bool Last)
{
    /// <summary>
    /// Build one from a slice that has already been taken, plus the total.
    ///
    /// <para><c>totalPages</c> is computed the way Spring does — ceiling of
    /// total over size, and 1 when size is zero rather than a division by
    /// zero. <c>last</c> is "this is the final page", which for an empty result
    /// is true.</para>
    /// </summary>
    public static PageResponse<T> From(IReadOnlyList<T> content, int page, int size, long totalElements)
    {
        int totalPages = size <= 0 ? 1 : (int)Math.Ceiling(totalElements / (double)size);
        bool last = page >= totalPages - 1;
        return new PageResponse<T>(content, page, size, totalElements, totalPages, last);
    }
}
