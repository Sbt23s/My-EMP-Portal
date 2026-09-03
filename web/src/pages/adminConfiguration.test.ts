import { describe, it, expect } from "vitest";
import { slugCode } from "./AdminConfiguration";

/**
 * The code is what gets stored on every record choosing this value, so it has
 * to be a stable identifier no matter what was typed into the label.
 */
describe("slugCode", () => {
  it("upper-cases and joins words with underscores", () => {
    expect(slugCode("Family function")).toBe("FAMILY_FUNCTION");
  });

  it("collapses runs of punctuation and spaces into one underscore", () => {
    expect(slugCode("Travel  &  Lodging")).toBe("TRAVEL_LODGING");
  });

  it("trims leading and trailing underscores", () => {
    // A label typed with a leading space must not produce "_MEDICAL", which
    // would sort and read wrongly everywhere it appears.
    expect(slugCode(" medical ")).toBe("MEDICAL");
    expect(slugCode("...urgent...")).toBe("URGENT");
  });

  it("keeps digits", () => {
    expect(slugCode("Tier 2 support")).toBe("TIER_2_SUPPORT");
  });

  it("returns empty for a label with nothing usable in it", () => {
    // The caller disables Add on an empty code, so this must be empty rather
    // than a lone underscore that would pass a non-blank check.
    expect(slugCode("!!!")).toBe("");
    expect(slugCode("")).toBe("");
  });

  it("caps the length at the column width", () => {
    expect(slugCode("a".repeat(200))).toHaveLength(80);
  });

  it("does not let a non-Latin label produce underscores only", () => {
    // Tamil labels are used in this portal; the derived code would be empty,
    // which correctly forces the administrator to type one.
    expect(slugCode("விடுமுறை")).toBe("");
  });
});
