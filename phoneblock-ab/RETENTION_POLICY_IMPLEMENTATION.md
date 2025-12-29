# Call Retention Policy Implementation

This document describes the automatic call retention policy feature that has been implemented for the PhoneBlock answerbot system.

## Overview

The retention policy feature allows users to automatically delete old call records after a configurable time period, reducing storage requirements and providing better privacy management.

## Features Implemented

### 1. Backend Changes (Java)

#### New Classes
- **`RetentionPeriod.java`** - Enum defining retention periods (NEVER, WEEK, MONTH, QUARTER, YEAR)
- **`CallRetentionService.java`** - Service for automatic cleanup with daily scheduling

#### Updated Classes
- **`CustomerOptions.java`** - Added retention policy interface methods
- **`CustomerConfig.java`** - Added retention configuration options
- **`AnswerBot.java`** - Starts retention service if enabled
- **`CreateABServlet.java`** - Added API endpoints for retention management
- **`Users.java`** - Added database methods for retention operations
- **`DBAnswerbotInfo.java`** - Updated constructor to include retention fields

#### Configuration
- **`.phoneblock.template`** - Added retention policy configuration options:
  - `retention-enabled=no` - Enable/disable automatic cleanup
  - `retention-period=MONTH` - Retention period setting

#### Database Changes Required
```sql
-- Add retention policy columns to ANSWERBOT_SIP table
ALTER TABLE ANSWERBOT_SIP ADD COLUMN RETENTION_ENABLED BOOLEAN DEFAULT FALSE;
ALTER TABLE ANSWERBOT_SIP ADD COLUMN RETENTION_PERIOD VARCHAR(10) DEFAULT 'MONTH';
```

#### New API Endpoints
- **`SetRetentionPolicy`** - Configure retention settings for a bot
- **`DeleteOldCalls`** - Manually delete calls older than specified time

### 2. Frontend Changes (Flutter/Dart)

#### Updated Protocol
- **`proto.dart`** - Added new request/response classes:
  - `SetRetentionPolicy` - Request to set retention policy
  - `DeleteOldCalls` - Request to delete old calls
  - Updated `AnswerbotInfo` with retention fields

#### Updated UI
- **`AnswerBotView.dart`** - Added retention policy settings section:
  - Toggle to enable/disable automatic cleanup
  - Dropdown to select retention period
  - Save button for retention settings

### 3. Automatic Cleanup Service

#### Features
- **Daily Scheduling** - Runs cleanup every 24 hours
- **Per-Bot Configuration** - Each bot can have different retention settings
- **Configurable Periods** - Support for 1 week, 1 month, 3 months, 1 year, or never
- **Logging** - Comprehensive logging of cleanup operations
- **Manual Trigger** - API endpoint for manual cleanup

#### Service Lifecycle
1. Started automatically when answerbot starts (if retention enabled)
2. Runs daily at fixed intervals
3. Queries all bots with retention enabled
4. Calculates cutoff time based on retention period
5. Deletes old call records
6. Logs cleanup results

## Configuration Examples

### Local Answerbot Configuration
```bash
# Enable retention with 30-day cleanup
retention-enabled=yes
retention-period=MONTH

# Disable retention (default)
retention-enabled=no
```

### API Usage Examples

#### Set Retention Policy
```json
{
  "id": 123,
  "enabled": true,
  "period": "MONTH"
}
```

#### Manual Cleanup
```json
{
  "id": 123,
  "cutoffTime": 1640995200000
}
```

## User Interface

### Retention Settings Section
The UI includes a new "Anruf-Aufbewahrung" section with:

1. **Enable/Disable Toggle**
   - "Alte Anrufprotokolle automatisch löschen"
   - Help text explaining the feature

2. **Retention Period Dropdown** (when enabled)
   - 1 Woche (1 week)
   - 1 Monat (1 month) - default
   - 3 Monate (3 months)
   - 1 Jahr (1 year)

3. **Save Button**
   - "Aufbewahrungseinstellungen speichern"
   - Shows progress dialog during save
   - Displays success/error messages

## Implementation Benefits

1. **Storage Management** - Automatically reduces database size
2. **Privacy Protection** - Removes old call data automatically
3. **User Control** - Configurable retention periods per bot
4. **Zero Maintenance** - Runs automatically without user intervention
5. **Flexible API** - Supports manual cleanup operations
6. **Comprehensive Logging** - Full audit trail of cleanup operations

## Deployment Notes

1. **Database Migration** - Add retention columns to ANSWERBOT_SIP table
2. **Configuration Update** - Users can update .phoneblock config file
3. **Service Restart** - Retention service starts automatically with answerbot
4. **UI Deployment** - Flutter web app needs rebuild and deployment

## Future Enhancements

Potential future improvements:
- **Custom Retention Periods** - Allow users to specify exact days
- **Selective Cleanup** - Retain calls based on duration or caller type
- **Backup Before Delete** - Export old calls before deletion
- **Statistics Dashboard** - Show cleanup statistics and storage savings
- **Notification System** - Alert users when cleanup occurs

## Testing

The implementation includes:
- **Unit Tests** - Test retention period calculations
- **Integration Tests** - Test API endpoints
- **Manual Testing** - UI functionality verification
- **Database Tests** - Verify cleanup operations

## Conclusion

The retention policy feature provides a comprehensive solution for automatic call record management, giving users control over their data retention while reducing storage requirements and improving privacy.
