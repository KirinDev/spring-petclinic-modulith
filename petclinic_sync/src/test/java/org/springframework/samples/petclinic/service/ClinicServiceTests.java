/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.samples.petclinic.owner.domain.Owner;
import org.springframework.samples.petclinic.owner.infrastructure.OwnerRepository;
import org.springframework.samples.petclinic.pet.domain.Pet;
import org.springframework.samples.petclinic.pet.infrastructure.PetRepository;
import org.springframework.samples.petclinic.pet.domain.PetType;
import org.springframework.samples.petclinic.visit.domain.Visit;
import org.springframework.samples.petclinic.vet.domain.Vet;
import org.springframework.samples.petclinic.vet.infrastructure.VetRepository;
import org.springframework.samples.petclinic.visit.infrastructure.VisitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test of the Service and the Repository layer.
 * <p>
 * ClinicServiceSpringDataJpaTests subclasses benefit from the following services provided
 * by the Spring TestContext Framework:
 * </p>
 * <ul>
 * <li><strong>Spring IoC container caching</strong> which spares us unnecessary set up
 * time between test execution.</li>
 * <li><strong>Dependency Injection</strong> of test fixture instances, meaning that we
 * don't need to perform application context lookups. See the use of
 * {@link Autowired @Autowired} on the <code> </code> instance variable, which uses
 * autowiring <em>by type</em>.
 * <li><strong>Transaction management</strong>, meaning each test method is executed in
 * its own transaction, which is automatically rolled back by default. Thus, even if tests
 * insert or otherwise change database state, there is no need for a teardown or cleanup
 * script.
 * <li>An {@link org.springframework.context.ApplicationContext ApplicationContext} is
 * also inherited and can be used for explicit bean lookup if necessary.</li>
 * </ul>
 *
 * @author Ken Krebs
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @author Michael Isvy
 * @author Dave Syer
 */
@DataJpaTest(includeFilters = @ComponentScan.Filter(Service.class))
// Ensure that if the mysql profile is active we connect to the real database:
@AutoConfigureTestDatabase(replace = Replace.NONE)
// @TestPropertySource("/application-postgres.properties")
class ClinicServiceTests {

	@Autowired
	protected OwnerRepository owners;

	@Autowired
	protected VetRepository vets;

	@Autowired
	protected VisitRepository visits;

	@Autowired
	protected PetRepository pets;

	Pageable pageable;

	@Test
	void shouldFindOwnersByLastName() {
		Page<Owner> owners = this.owners.findByLastName("Davis", pageable);
		assertThat(owners).hasSize(2);

		owners = this.owners.findByLastName("Daviss", pageable);
		assertThat(owners).isEmpty();
	}

	@Test
	void shouldFindSingleOwnerWithPet() {
		Owner owner = this.owners.findById(1);
		List<Pet> pets = this.pets.findByOwnerId(1);
		assertThat(owner.getLastName()).startsWith("Franklin");
		assertThat(pets).hasSize(1);
		assertThat(pets.get(0).getType()).isNotNull();
		assertThat(pets.get(0).getType().getName()).isEqualTo("cat");
	}

	@Test
	@Transactional
	void shouldInsertOwner() {
		Page<Owner> owners = this.owners.findByLastName("Schultz", pageable);
		int found = (int) owners.getTotalElements();

		Owner owner = new Owner();
		owner.setFirstName("Sam");
		owner.setLastName("Schultz");
		owner.setAddress("4, Evans Street");
		owner.setCity("Wollongong");
		owner.setTelephone("4444444444");
		this.owners.save(owner);
		assertThat(owner.getId().longValue()).isNotEqualTo(0);

		owners = this.owners.findByLastName("Schultz", pageable);
		assertThat(owners.getTotalElements()).isEqualTo(found + 1);
	}

	@Test
	@Transactional
	void shouldUpdateOwner() {
		Owner owner = this.owners.findById(1);
		String oldLastName = owner.getLastName();
		String newLastName = oldLastName + "X";

		owner.setLastName(newLastName);
		this.owners.save(owner);

		// retrieving new name from database
		owner = this.owners.findById(1);
		assertThat(owner.getLastName()).isEqualTo(newLastName);
	}

	@Test
	void shouldFindAllPetTypes() {
		Collection<PetType> petTypes = this.pets.findPetTypes();

		PetType petType1 = EntityUtils.getById(petTypes, PetType.class, 1);
		assertThat(petType1.getName()).isEqualTo("cat");
		PetType petType4 = EntityUtils.getById(petTypes, PetType.class, 4);
		assertThat(petType4.getName()).isEqualTo("snake");
	}

	@Test
	@Transactional
	void shouldInsertPetIntoDatabaseAndGenerateId() {
		List<Pet> pets = this.pets.findByOwnerId(6);
		int found = pets.size();
		Pet pet = new Pet();
		pet.setOwnerId(6);
		pet.setName("bowser");
		Collection<PetType> types = this.pets.findPetTypes();
		pet.setType(EntityUtils.getById(types, PetType.class, 2));
		pet.setBirthDate(LocalDate.now());

		this.pets.save(pet);

		List<Pet> pets2 = this.pets.findByOwnerId(6);
		assertThat(pets2.size()).isEqualTo(found + 1);
		// checks that id has been generated
		pet = this.pets.findByOwnerIdAndName(6, "bowser");
		assertThat(pet.getId()).isNotNull();
	}

	@Test
	@Transactional
	void shouldUpdatePetName() throws Exception {
		Pet pet7 = this.pets.findById(7);
		String oldName = pet7.getName();

		String newName = oldName + "X";
		pet7.setName(newName);
		this.pets.save(pet7);

		pet7 = this.pets.findById(7);
		assertThat(pet7.getName()).isEqualTo(newName);
	}

	@Test
	void shouldFindVets() {
		Collection<Vet> vets = this.vets.findAll();

		Vet vet = EntityUtils.getById(vets, Vet.class, 3);
		assertThat(vet.getLastName()).isEqualTo("Douglas");
		assertThat(vet.getNrOfSpecialties()).isEqualTo(2);
		assertThat(vet.getSpecialties().get(0).getName()).isEqualTo("dentistry");
		assertThat(vet.getSpecialties().get(1).getName()).isEqualTo("surgery");
	}

	@Test
	@Transactional
	void shouldAddNewVisitForPet() {
		Owner owner6 = this.owners.findById(6);
		Pet pet7 = this.pets.findById(7);
		List<Visit> visits = this.visits.findByPetId(7);
		int found = visits.size();
		Visit visit = new Visit();
		visit.setPet(7);
		visit.setDescription("test");

		this.visits.save(visit);
		List<Visit> visits2 = this.visits.findByPetId(7);

		assertThat(visits2) //
			.hasSize(found + 1) //
			.allMatch(value -> value.getId() != null);
	}

	@Test
	void shouldFindVisitsByPetId() throws Exception {
		Collection<Visit> visits = this.visits.findByPetId(7);

		assertThat(visits) //
			.hasSize(2) //
			.element(0)
			.extracting(Visit::getDate)
			.isNotNull();
	}

}
