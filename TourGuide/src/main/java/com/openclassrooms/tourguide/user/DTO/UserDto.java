package com.openclassrooms.tourguide.user.DTO;



import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class UserDto {

    @JsonIgnore
    private UUID userId;

    private LocationDto touristLocation;

    private List<AttractionDto> touristAttractions;
}
