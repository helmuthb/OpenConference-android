package com.openconference.model.backend.schedule

import com.openconference.model.Session
import com.openconference.model.database.dao.LocationDao
import com.openconference.model.database.dao.SessionDao
import com.openconference.model.database.dao.SpeakerDao
import com.openconference.model.notification.AddOrRescheduleNotificationCommand
import com.openconference.model.notification.NotificationScheduler
import com.openconference.model.notification.NotificationSchedulerCommand
import com.openconference.model.notification.RemoveScheduledNotificationCommand
import rx.Observable
import java.util.*

/**
 * Responsible to sync the local database with your conferences backend
 * @author Hannes Dorfmann
 */
class ScheduleSync(
    private val backend: BackendScheduleAdapter,
    private val notificationScheduler: NotificationScheduler,
    private val sessionDao: SessionDao,
    private val speakerDao: SpeakerDao,
    private val locationDao: LocationDao) {


  /**
   * Loads the data from backend and stores it into the local persistent storage for offline support
   */
  fun executeSync(): Observable<Boolean> = Observable.combineLatest(backend.getLocations(),
      backend.getSpeakers(), backend.getSessions(), {

    locationsResponse, speakersResponse, sessionsResponse ->
    if (locationsResponse.isNewerDataAvailable
        || speakersResponse.isNewerDataAvailable
        || sessionsResponse.isNewerDataAvailable) {

      // At least one data has been changed, so update local database
      // TODO move all sessions into transaction
      val allSessions: List<Session> =
          sessionDao.getSessions()
              .toBlocking()
              .first()


      val transaction = sessionDao.getBriteDatabase().newTransaction()
      try {

        // Update locations
        if (locationsResponse.isNewerDataAvailable) {
          locationDao.removeAll().toBlocking().first()
          locationsResponse.data.forEach {
            locationDao.insertOrUpdate(it.id(), it.name()).toBlocking().first()
          }
        }

        // Update Speakers
        if (speakersResponse.isNewerDataAvailable) {
          speakerDao.removeAll().toBlocking().first()
          speakersResponse.data.forEach {
            speakerDao.insertOrUpdate(it.id(), it.name(), it.info(), it.profilePic(), it.company(),
                it.jobTitle(), it.link1(), it.link2(), it.link3()).toBlocking().first()
          }
        }

        val notificaionSchedulerCommands = ArrayList<NotificationSchedulerCommand>()
        // Update Sessions
        if (sessionsResponse.isNewerDataAvailable) {

          val newSessionData: HashMap<String, Session> = sessionsResponse
              .data.fold(HashMap<String, Session>(), { map, session ->
            map.put(session.id(), session)
            map
          })

          allSessions.forEach {

            val newSession = newSessionData[it.id()]

            if (newSession == null) {
              // Session has been removed
              sessionDao.remove(it.id()).toBlocking().first()
              if (it.favorite()) {
                notificaionSchedulerCommands.add(
                    RemoveScheduledNotificationCommand(it, notificationScheduler))
              }
            } else {
              // check if time changed
              if (it.favorite() && it.startTime() != newSession.startTime()) {
                notificaionSchedulerCommands.add(
                    AddOrRescheduleNotificationCommand(newSession, notificationScheduler))
              }

              sessionDao.insertOrUpdate(newSession, it.favorite()).toBlocking().first()

              // Handled session, so can be removed
              newSessionData.remove(newSession.id())
            }
          }

          // Contains only unhandled values, so then new Sessions should be added
          newSessionData.values.forEach {
            sessionDao.insertOrUpdate(it, false).toBlocking().first()
          }
        }

        transaction.markSuccessful()
        notificaionSchedulerCommands.forEach { it.execute() } // Execute only if transaction was successful
      } finally {
        transaction.end()
      }

    }

    true
  }
  ).share()

}