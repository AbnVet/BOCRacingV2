# CODEX - Development Rules

## v1 Reference Policy

**Location**: `D:\Projects\BOCRacePlugin`

- **READ-ONLY**: Never edit v1 code. Never copy/paste code from v1.
- **Reference Only**: Use v1 to understand behavior and requirements, not to migrate code.
- **Fresh Implementation**: All v2 code must be written from scratch with clean architecture.

## Development Workflow

### Compilation Guardrail
- **Every change must compile**: Run `mvn -q package` before committing.
- **Small diffs only**: Make incremental, testable changes.
- **No broken builds**: Never commit code that doesn't compile.

### Code Quality Rules

1. **No Dead Code**
   - Remove unused imports, methods, and classes immediately.
   - Keep the codebase lean and maintainable.

2. **File Change Planning**
   - List all files you plan to change before making changes.
   - Document the purpose of each change.

3. **No Deployment Steps**
   - Do not include JAR copying or deployment instructions in commits.
   - Focus on code changes only.

## Change Checklist

Before committing:
- [ ] All changes compile (`mvn -q package`)
- [ ] No dead code introduced
- [ ] Files changed are documented
- [ ] No v1 code copied/pasted
- [ ] Small, focused diff
