package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.openclassrooms.tourguide.user.DTO.AttractionDto;
import com.openclassrooms.tourguide.user.DTO.LocationDto;
import com.openclassrooms.tourguide.user.DTO.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;

import rewardCentral.RewardCentral;
import tripPricer.Provider;
import tripPricer.TripPricer;

@Service
public class TourGuideService {
	private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	boolean testMode = true;

	public ExecutorService getExecutorService() {
		return executorService;
	}

	private final ExecutorService executorService = Executors.newFixedThreadPool(1000);

	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;

		Locale.setDefault(Locale.US);

		if (testMode) {
			logger.info("TestMode enabled");
			logger.debug("Initializing users");
			initializeInternalUsers();
			logger.debug("Finished initializing users");
		}
		tracker = new Tracker(this);
		addShutDownHook();

	}
	public UserDto getClosestFiveTouristAttractionsToTheUser(String userName) throws InterruptedException {
		User user = getUser(userName);
		//User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
		UserDto userDto = new UserDto();
		List<AttractionDto> attractionDtoList = new ArrayList<>();
		UUID userUuid = user.getUserId();
		RewardCentral rewardCentral = new RewardCentral();

		// tourist location
		trackUserLocation(user);
		TimeUnit.SECONDS.sleep(2);
		VisitedLocation visitedLocation = user.getVisitedLocations().get(user.getVisitedLocations().size()-1);
		double longitude = visitedLocation.location.longitude;
		double latitude = visitedLocation.location.latitude;
		LocationDto locationDtoTourist = new LocationDto();
		locationDtoTourist.setLatitude(latitude);
		locationDtoTourist.setLongitude(longitude);
		userDto.setTouristLocation(locationDtoTourist);

		// attraction name, attraction location, distance between tourist, attraction and reward
		List<Attraction> attractions = getFiveNearByAttractions(visitedLocation);
		for (Attraction attraction : attractions){
			//attraction name
			AttractionDto attractionDto = new AttractionDto();
			String nameAttraction = attraction.attractionName;
			attractionDto.setAttractionName(nameAttraction);

			//attraction location
			LocationDto locationDtoAttraction = new LocationDto();
			locationDtoAttraction.setLatitude(attraction.latitude);
			locationDtoAttraction.setLongitude(attraction.longitude);
			attractionDto.setAttractionLocation(locationDtoAttraction);

			//attraction distance
			Location newLocation = new Location(attraction.latitude, attraction.longitude);
			attractionDto.setDistanceBetweenTouristAndAttraction(rewardsService.getDistance(visitedLocation.location, newLocation));

			// rewardPoint
			UUID attractionUuid = attraction.attractionId;
			Integer rewardPoint = rewardCentral.getAttractionRewardPoints(attractionUuid,userUuid);
			attractionDto.setRewardPointGainForTheAttraction(rewardPoint);

			attractionDtoList.add(attractionDto);
		}

		userDto.setTouristAttractions(attractionDtoList);
		return userDto;

	}
	public List<Attraction> getFiveNearByAttractions(VisitedLocation visitedLocation) {
		List<Attraction> nearbyAttractions = new ArrayList<>();
		for (Attraction attraction : gpsUtil.getAttractions()) {
			if (rewardsService.isWithinAttractionProximity(attraction, visitedLocation.location)){
				nearbyAttractions.add(attraction);
			}
			if (nearbyAttractions.size() >= 5) {
				break;
			}
		}
		return nearbyAttractions;
	}


	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	public VisitedLocation getUserLocation(User user) {
		VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0) ? user.getLastVisitedLocation()
				: trackUserLocation(user);
		return visitedLocation;
	}

	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	public List<User> getAllUsers() {
		return internalUserMap.values().stream().collect(Collectors.toList());
	}

	public void addUser(User user) {
		if (!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}

	public List<Provider> getTripDeals(User user) {
		int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();

		List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);

		user.setTripDeals(providers);

		return providers;
	}

	public VisitedLocation trackUserLocation(User user) {
		Locale.setDefault(Locale.US);
		CompletableFuture.supplyAsync(()-> {
			VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
			user.addToVisitedLocations(visitedLocation);
			return visitedLocation;
		}, executorService).thenAccept(location -> {
			rewardsService.calculateRewards(user);
		}).exceptionally(throwable -> {
			System.out.println("ERROR : "+throwable.getMessage());
			return null;
		});
		return null;
    }

	public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
		List<Attraction> nearbyAttractions = new ArrayList<>();
		for (Attraction attraction : gpsUtil.getAttractions()) {
			if (rewardsService.isWithinAttractionProximity(attraction, visitedLocation.location)) {
				nearbyAttractions.add(attraction);
			}
		}

		return nearbyAttractions;
	}

	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				tracker.stopTracking();
			}
		});
	}

	/**********************************************************************************
	 * 
	 * Methods Below: For Internal Testing
	 * 
	 **********************************************************************************/
	private static final String tripPricerApiKey = "test-server-api-key";
	// Database connection will be used for external users, but for testing purposes
	// internal users are provided and stored in memory
	private final Map<String, User> internalUserMap = new HashMap<>();

	private void initializeInternalUsers() {
		IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
			String userName = "internalUser" + i;
			String phone = "000";
			String email = userName + "@tourGuide.com";
			User user = new User(UUID.randomUUID(), userName, phone, email);
			generateUserLocationHistory(user);

			internalUserMap.put(userName, user);
		});
		logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
	}

	private void generateUserLocationHistory(User user) {
		IntStream.range(0, 3).forEach(i -> {
			user.addToVisitedLocations(new VisitedLocation(user.getUserId(),
					new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
		});
	}

	private double generateRandomLongitude() {
		double leftLimit = -180;
		double rightLimit = 180;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}
	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
		double rightLimit = 85.05112878;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

}
