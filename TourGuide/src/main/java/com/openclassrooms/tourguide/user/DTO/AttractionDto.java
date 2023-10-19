package com.openclassrooms.tourguide.user.DTO;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.UUID;

@Data
public class AttractionDto {

    @JsonIgnore
    private UUID attractionId;

    private String attractionName;

    private LocationDto attractionLocation;

    private Double distanceBetweenTouristAndAttraction;

    private Integer rewardPointGainForTheAttraction;

    public void setAttractionLocation(double latitude, double longitude) {
    }
}
